package com.pluto.ndi.backup.util;

import com.pluto.ndi.backup.config.RestoreTarget;
import com.pluto.ndi.backup.domain.RestoreFailure;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RestoreSafetyTest {
    private Map<String,String> configuration() {
        return new HashMap<>(Map.of("NDI_RESTORE_JDBC_URL","jdbc:mysql://127.0.0.1:3308/ndi_restore?serverTimezone=UTC",
                "NDI_RESTORE_DB_USERNAME","restore_user","NDI_RESTORE_DB_PASSWORD","unit-db-secret",
                "NDI_RESTORE_MINIO_ENDPOINT","http://127.0.0.1:19010","NDI_RESTORE_MINIO_ACCESS_KEY","restore-key","NDI_RESTORE_MINIO_SECRET_KEY","unit-minio-secret"));
    }
    @Test void requiresDedicatedConfigurationAndExactDatabaseConfirmation() {
        assertThatThrownBy(()->RestoreTarget.from(Map.of("NDI_DATASOURCE_URL","jdbc:mysql://127.0.0.1:3307/ndi"),"ndi")).isInstanceOf(RestoreFailure.class);
        assertThatThrownBy(()->RestoreTarget.from(configuration(),"ndi")).isInstanceOf(RestoreFailure.class);
        var valid=RestoreTarget.from(configuration(),"ndi_restore");
        assertThat(valid.toString()).doesNotContain("unit-db-secret","unit-minio-secret","restore-key","jdbc:mysql");
        assertThat(valid.database()).isEqualTo("ndi_restore");
    }
    @Test void rejectsUnsafeDriverOptionsEmbeddedCredentialsAndSystemSchemas() {
        for(var url:List.of("jdbc:mysql://127.0.0.1:3308/ndi_restore?allowMultiQueries=true",
                "jdbc:mysql://127.0.0.1:3308/ndi_restore?password=secret","jdbc:mysql://user:secret@127.0.0.1/ndi_restore",
                "jdbc:mysql://127.0.0.1/mysql","jdbc:mysql://127.0.0.1/ndi_restore?autoDeserialize=true")) {
            var values=configuration();values.put("NDI_RESTORE_JDBC_URL",url);
            assertThatThrownBy(()->RestoreTarget.from(values,url.endsWith("mysql")?"mysql":"ndi_restore")).isInstanceOf(RestoreFailure.class);
        }
        var values=configuration();values.put("NDI_RESTORE_MINIO_ENDPOINT","http://user:secret@127.0.0.1:9000");
        assertThatThrownBy(()->RestoreTarget.from(values,"ndi_restore")).isInstanceOf(RestoreFailure.class);
    }
    @Test void sqlReaderKeepsQuotedSemicolonsAndStreamsOnlyWhitelistedStatements() throws Exception {
        var sql="-- 归档注释;\nSET NAMES utf8mb4;\nCREATE TABLE `t` (`v` TEXT COMMENT '引号;内容');\nINSERT INTO `t` (`v`) VALUES (CONVERT(X'3b' USING utf8mb4));\n";
        var statements=new ArrayList<String>();RestoreSqlReader.read(new StringReader(sql),Set.of("t"),statements::add);
        assertThat(statements).hasSize(3);assertThat(statements.get(1)).contains("引号;内容");
    }
    @Test void sqlReaderRejectsOverwriteCrossDatabaseAndIncompleteSql() {
        for(var sql:List.of("DROP TABLE `t`;","USE mysql;","INSERT INTO `other` VALUES (1);","INSERT INTO db.`t` VALUES (1);","INSERT INTO `t` VALUES ('未结束);","/* 未结束")) {
            assertThatThrownBy(()->RestoreSqlReader.read(new StringReader(sql),Set.of("t"),statement->{ })).isInstanceOf(IOException.class);
        }
    }
    @Test void cliHelpAndInvalidArgumentsDoNotReadApplicationCredentials() throws Exception {
        var output=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();
        var out=new PrintStream(output,true,java.nio.charset.StandardCharsets.UTF_8);var err=new PrintStream(errors,true,java.nio.charset.StandardCharsets.UTF_8);
        assertThat(BackupRestoreCli.run(new String[]{"--help"},Map.of(),out,err)).isZero();
        assertThat(output.toString(java.nio.charset.StandardCharsets.UTF_8)).contains("不提供强制覆盖");
        assertThat(BackupRestoreCli.run(new String[]{"restore","--force","yes"},Map.of("NDI_DATASOURCE_PASSWORD","should-not-leak"),out,err)).isEqualTo(2);
        assertThat(errors.toString(java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("should-not-leak");
    }
}
