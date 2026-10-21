package com.pluto.ndi.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.backup.config.RestoreTarget;
import com.pluto.ndi.backup.domain.*;
import com.pluto.ndi.backup.mapper.RestoreTargetMapper;
import com.pluto.ndi.backup.util.*;
import io.minio.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.zip.ZipFile;
import static com.pluto.ndi.backup.domain.RestoreFailure.Reason.*;

/** 离线恢复编排，不是 Spring Bean，不读取应用配置，也不启动后台任务。 */
public final class BackupRestoreService {
    private final ObjectMapper json;
    public BackupRestoreService(ObjectMapper json) { this.json=json; }
    public BackupManifest verify(Path archive,String checksum,long maximumBytes) {
        try {
            var manifest=BackupArchiveVerifier.verify(archive,checksum,maximumBytes,json);
            var tables=new HashSet<String>();
            for(var table:manifest.tables()) if(table.rows()<0 || !table.name().matches("[a-zA-Z0-9_]{1,64}") || !tables.add(table.name())) throw new RestoreFailure(ARCHIVE_INVALID);
            if(!tables.containsAll(Set.of("flyway_schema_history","identity_user_account","project_project","document_object","system_audit_event"))) throw new RestoreFailure(ARCHIVE_INVALID);
            for(var object:manifest.objects()) {
                if(object.bucket().isBlank() || object.objectKey().isBlank() || object.mediaType()==null || object.mediaType().isBlank()
                        || object.mediaType().contains("\r") || object.mediaType().contains("\n")) throw new RestoreFailure(ARCHIVE_INVALID);
            }
            try(var zip=new ZipFile(archive.toFile());var reader=new InputStreamReader(zip.getInputStream(zip.getEntry("database.sql")),StandardCharsets.UTF_8)) {
                RestoreSqlReader.read(reader,tables,sql->{ });
            }
            return manifest;
        } catch(RestoreFailure failure) {throw failure;}
        catch(Exception failure) {throw new RestoreFailure(ARCHIVE_INVALID,failure);}
    }
    public record Report(String status,UUID backupId,int tables,int objects,int invalidatedSessions,int interruptedBackups,Instant completedAt) { }
    public Report restore(Path archive,String checksum,long maximumBytes,RestoreTarget target,String operator) {
        var manifest=verify(archive,checksum,maximumBytes);
        if(operator==null || operator.isBlank() || operator.length()>100) throw new RestoreFailure(CONFIG_INVALID);
        var http=new okhttp3.OkHttpClient.Builder().callTimeout(Duration.ofSeconds(120)).readTimeout(Duration.ofSeconds(30)).build();
        boolean started=false;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            var credentials=new Properties();credentials.setProperty("user",target.username());credentials.setProperty("password",target.password());
            credentials.setProperty("allowMultiQueries","false");credentials.setProperty("allowLoadLocalInfile","false");credentials.setProperty("autoDeserialize","false");
            credentials.setProperty("connectTimeout","10000");credentials.setProperty("socketTimeout","120000");credentials.setProperty("connectionTimeZone","UTC");
            var minio=MinioClient.builder().endpoint(target.minioEndpoint()).credentials(target.accessKey(),target.secretKey()).httpClient(http).build();
            try(var connection=DriverManager.getConnection(target.jdbcUrl(),credentials);var zip=new ZipFile(archive.toFile())) {
                if(!target.database().equals(connection.getCatalog())) throw new RestoreFailure(CONFIG_INVALID);
                String lock="ndi.restore."+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(target.database().getBytes(StandardCharsets.UTF_8))).substring(0,32);
                try(var query=connection.prepareStatement("SELECT GET_LOCK(?,0)")) {
                    query.setString(1,lock);
                    try(var result=query.executeQuery()) {if(!result.next() || result.getInt(1)!=1) throw new RestoreFailure(TARGET_BUSY);}
                }
                RestoreTargetMapper.requireEmpty(connection);
                // 要求独立空对象存储实例；即使已有空桶也拒绝，避免混入业务环境。
                if(!minio.listBuckets().isEmpty()) throw new RestoreFailure(TARGET_NOT_EMPTY);
                started=true;
                connection.setAutoCommit(false);
                var tables=new HashSet<String>();manifest.tables().forEach(table->tables.add(table.name()));
                try(var reader=new InputStreamReader(zip.getInputStream(zip.getEntry("database.sql")),StandardCharsets.UTF_8)) {
                    RestoreSqlReader.read(reader,tables,sql->{try(var statement=connection.createStatement()) {statement.setQueryTimeout(120);statement.execute(sql);}});
                }
                RestoreTargetMapper.verifyRows(connection,manifest);
                RestoreTargetMapper.verifyForeignKeys(connection);
                var buckets=new TreeSet<String>();manifest.objects().forEach(object->buckets.add(object.bucket()));
                for(var bucket:buckets) minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                for(var object:manifest.objects()) {
                    try(var input=zip.getInputStream(zip.getEntry(object.entry()))) {
                        minio.putObject(PutObjectArgs.builder().bucket(object.bucket()).object(object.objectKey()).contentType(object.mediaType())
                                .extraHeaders(Map.of("If-None-Match","*")).stream(input,object.size(),-1L).build());
                    }
                    var stat=minio.statObject(StatObjectArgs.builder().bucket(object.bucket()).object(object.objectKey()).build());
                    if(stat.size()!=object.size() || !object.mediaType().equals(stat.contentType())) throw new RestoreFailure(RESTORE_FAILED);
                    var digest=MessageDigest.getInstance("SHA-256");
                    try(var input=minio.getObject(GetObjectArgs.builder().bucket(object.bucket()).object(object.objectKey()).build())) {
                        var buffer=new byte[65536];int length;
                        while((length=input.read(buffer))!=-1) digest.update(buffer,0,length);
                    }
                    if(!object.sha256().equals(HexFormat.of().formatHex(digest.digest()))) throw new RestoreFailure(RESTORE_FAILED);
                }
                var expectedBuckets=new HashSet<>(buckets);
                for(var bucket:minio.listBuckets()) {
                    if(!expectedBuckets.remove(bucket.name())) throw new RestoreFailure(RESTORE_FAILED);
                    var expectedKeys=new HashSet<String>();
                    manifest.objects().stream().filter(object->object.bucket().equals(bucket.name())).forEach(object->expectedKeys.add(object.objectKey()));
                    for(var result:minio.listObjects(ListObjectsArgs.builder().bucket(bucket.name()).recursive(true).build())) {
                        if(!expectedKeys.remove(result.get().objectName())) throw new RestoreFailure(RESTORE_FAILED);
                    }
                    if(!expectedKeys.isEmpty()) throw new RestoreFailure(RESTORE_FAILED);
                }
                if(!expectedBuckets.isEmpty()) throw new RestoreFailure(RESTORE_FAILED);
                if(!checksum.equals(BackupChecksum.sha256(archive))) throw new RestoreFailure(RESTORE_FAILED);
                var cleanup=RestoreTargetMapper.sanitizeOperationalState(connection,tables,manifest,operator,checksum);
                connection.commit();
                return new Report("RESTORED",manifest.backupId(),manifest.tables().size(),manifest.objects().size(),cleanup.invalidatedSessions(),cleanup.interruptedBackups(),Instant.now());
            }
        } catch(RestoreFailure failure) {throw failure;}
        catch(Exception failure) {throw new RestoreFailure(started?RESTORE_FAILED:TARGET_UNAVAILABLE,failure);}
        finally {http.dispatcher().cancelAll();http.dispatcher().executorService().shutdown();http.connectionPool().evictAll();}
    }
}
