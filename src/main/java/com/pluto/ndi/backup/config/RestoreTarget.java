package com.pluto.ndi.backup.config;

import com.pluto.ndi.backup.domain.RestoreFailure;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 专用恢复配置；禁止回退读取应用的 datasource、MinIO 配置或密码。 */
public record RestoreTarget(String jdbcUrl, String username, String password, String database,
        String minioEndpoint, String accessKey, String secretKey) {
    public static RestoreTarget from(Map<String,String> environment, String confirmation) {
        try {
            String url = required(environment,"NDI_RESTORE_JDBC_URL");
            if (!url.startsWith("jdbc:mysql://")) throw invalid();
            var jdbc = URI.create(url.substring(5));
            String database = jdbc.getPath() == null ? "" : jdbc.getPath().substring(1);
            if (jdbc.getHost() == null || jdbc.getUserInfo() != null || jdbc.getFragment() != null
                    || !database.matches("[a-zA-Z0-9_]{1,64}") || !database.equals(confirmation)
                    || Set.of("mysql","sys","information_schema","performance_schema").contains(database.toLowerCase(Locale.ROOT))) throw invalid();
            if (jdbc.getRawQuery() != null) for (var option : jdbc.getRawQuery().split("&")) {
                String key = URLDecoder.decode(option.split("=",2)[0],StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (!Set.of("servertimezone","sslmode","usessl","allowpublickeyretrieval","connecttimeout","sockettimeout","characterencoding","connectioncollation").contains(key)) throw invalid();
            }
            String endpoint = required(environment,"NDI_RESTORE_MINIO_ENDPOINT");
            var minio = URI.create(endpoint);
            if (!Set.of("http","https").contains(minio.getScheme()) || minio.getHost() == null || minio.getUserInfo() != null
                    || minio.getQuery() != null || minio.getFragment() != null || !(minio.getPath().isEmpty() || minio.getPath().equals("/"))) throw invalid();
            return new RestoreTarget(url,required(environment,"NDI_RESTORE_DB_USERNAME"),required(environment,"NDI_RESTORE_DB_PASSWORD"),database,
                    endpoint,required(environment,"NDI_RESTORE_MINIO_ACCESS_KEY"),required(environment,"NDI_RESTORE_MINIO_SECRET_KEY"));
        } catch (RestoreFailure failure) { throw failure; }
        catch (Exception failure) { throw invalid(); }
    }
    private static String required(Map<String,String> environment,String key) {
        var value = environment.get(key); if (value == null || value.isBlank()) throw invalid(); return value;
    }
    private static RestoreFailure invalid() { return new RestoreFailure(RestoreFailure.Reason.CONFIG_INVALID); }
    @Override public String toString() { return "RestoreTarget[凭据与连接信息已隐藏]"; }
}
