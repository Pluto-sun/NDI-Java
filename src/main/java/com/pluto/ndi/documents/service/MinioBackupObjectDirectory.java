package com.pluto.ndi.documents.service;

import com.pluto.ndi.documents.config.StorageProperties;
import com.pluto.ndi.documents.domain.DocumentErrorCode;
import com.pluto.ndi.documents.util.ObjectStorageKeys;
import com.pluto.ndi.shared.web.BusinessException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 独立的只读存储适配器，不与上传和导出 Bean 的替换、代理或测试桩耦合。 */
@Component
class MinioBackupObjectDirectory implements BackupObjectDirectory {
    private final StorageProperties properties;
    private final MinioClient reader;
    MinioBackupObjectDirectory(StorageProperties properties) {
        this.properties = properties;
        this.reader = MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .httpClient(new okhttp3.OkHttpClient.Builder().callTimeout(Duration.ofSeconds(120))
                        .readTimeout(Duration.ofSeconds(30)).build()).build();
    }
    @Override public InputStream openBackupObject(String bucket, String objectKey) {
        try { return reader.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build()); }
        catch (MinioException failure) { throw new BusinessException(DocumentErrorCode.STORAGE_UNAVAILABLE); }
    }
    @Override public ObjectLocation projectExportLocation(UUID exportId) {
        return new ObjectLocation(properties.bucket(), ObjectStorageKeys.projectExport(exportId));
    }
}
