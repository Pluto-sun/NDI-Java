package com.pluto.ndi.documents.service;

import com.pluto.ndi.documents.config.StorageProperties;
import com.pluto.ndi.documents.domain.DocumentErrorCode;
import com.pluto.ndi.shared.web.BusinessException;

import io.minio.*;
import io.minio.errors.MinioException;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

@Component
class MinioImportWorkbookDirectory implements ImportWorkbookDirectory {
    private final MinioClient client;
    private final StorageProperties properties;

    MinioImportWorkbookDirectory(StorageProperties properties) {
        this.properties = properties;
        client =
                MinioClient.builder()
                        .endpoint(properties.endpoint())
                        .credentials(properties.accessKey(), properties.secretKey())
                        .build();
    }

    public Stored store(UUID id, byte[] bytes) {
        return storeFor("project", id, bytes);
    }

    public Stored storeFor(String module, UUID id, byte[] bytes) {
        if (!java.util.Set.of("project", "production", "revenue").contains(module))
            throw new IllegalArgumentException("导入模块无效");
        var location = new Stored(properties.bucket(), module + "-imports/" + id + ".xlsx");
        try (var stream = new ByteArrayInputStream(bytes)) {
            if (!client.bucketExists(
                    BucketExistsArgs.builder().bucket(location.bucket()).build())) {
                if (!properties.autoCreateBucket())
                    throw new BusinessException(DocumentErrorCode.STORAGE_UNAVAILABLE);
                try {
                    client.makeBucket(MakeBucketArgs.builder().bucket(location.bucket()).build());
                } catch (MinioException concurrent) {
                    if (!client.bucketExists(
                            BucketExistsArgs.builder().bucket(location.bucket()).build()))
                        throw concurrent;
                }
            }
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(location.bucket())
                            .object(location.objectKey())
                            .stream(stream, (long) bytes.length, -1L)
                            .contentType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .build());
            return location;
        } catch (MinioException | IOException failure) {
            throw new BusinessException(DocumentErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    public byte[] read(String bucket, String key) {
        try (var stream =
                client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            var bytes = stream.readNBytes(10 * 1024 * 1024 + 1);
            if (bytes.length > 10 * 1024 * 1024)
                throw new BusinessException(DocumentErrorCode.INTEGRITY_FAILED);
            return bytes;
        } catch (MinioException | IOException failure) {
            throw new BusinessException(DocumentErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    public void delete(String bucket, String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException failure) {
            throw new BusinessException(DocumentErrorCode.STORAGE_UNAVAILABLE);
        }
    }
}
