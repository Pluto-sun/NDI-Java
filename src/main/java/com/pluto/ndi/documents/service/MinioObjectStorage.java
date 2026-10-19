package com.pluto.ndi.documents.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import com.pluto.ndi.documents.config.StorageProperties;
import com.pluto.ndi.documents.domain.DocumentErrorCode;
import com.pluto.ndi.shared.web.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.SourceObject;
import io.minio.errors.MinioException;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 将待上传对象复制到仅由服务端写入的正式对象键，防止预签名链接到期前覆盖已验证内容。 */
@Component
class MinioObjectStorage implements ObjectStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinioObjectStorage.class);
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private final StorageProperties properties;
    private final MinioClient internalClient;
    private final MinioClient publicSigner;

    MinioObjectStorage(StorageProperties properties) {
        this.properties = properties;
        this.internalClient = MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        this.publicSigner = MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Override
    public void ensureBucket(String bucket) {
        try {
            if (internalClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                return;
            }
            if (!properties.autoCreateBucket()) {
                throw new BusinessException(DocumentErrorCode.STORAGE_UNAVAILABLE);
            }
            try {
                internalClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            } catch (MinioException concurrentCreate) {
                if (!internalClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    throw concurrentCreate;
                }
            }
        } catch (MinioException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public String signUpload(String bucket, String stagingObjectKey) {
        try {
            return publicSigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.PUT).bucket(bucket).object(stagingObjectKey)
                    .expiry(10, TimeUnit.MINUTES).build());
        } catch (MinioException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public String sealAndVerify(
            String bucket, String stagingObjectKey, String objectKey,
            long expectedSize, String expectedMediaType, String expectedSha256
    ) {
        try {
            var staging = internalClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(stagingObjectKey).build());
            if (staging.size() != expectedSize || !expectedMediaType.equals(staging.contentType())) {
                throw new BusinessException(DocumentErrorCode.INTEGRITY_FAILED);
            }
            internalClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket).object(objectKey)
                    .source(SourceObject.builder().bucket(bucket).object(stagingObjectKey).build())
                    .build());
            var sealed = internalClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(objectKey).build());
            if (sealed.size() != expectedSize || !expectedMediaType.equals(sealed.contentType())
                    || !expectedSha256.equals(sha256(bucket, objectKey, expectedMediaType))) {
                throw new BusinessException(DocumentErrorCode.INTEGRITY_FAILED);
            }
            return sealed.etag();
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new BusinessException(DocumentErrorCode.NOT_AVAILABLE);
            }
            throw storageFailure(exception);
        } catch (MinioException | IOException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public String signDownload(String bucket, String objectKey) {
        try {
            return publicSigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET).bucket(bucket).object(objectKey)
                    .expiry(5, TimeUnit.MINUTES).build());
        } catch (MinioException exception) {
            throw storageFailure(exception);
        }
    }

    private String sha256(String bucket, String objectKey, String mediaType) throws MinioException, IOException {
        try (var stream = internalClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            var digest = MessageDigest.getInstance("SHA-256");
            var prefix = stream.readNBytes(8);
            if (!matchesSignature(prefix, mediaType)) {
                throw new BusinessException(DocumentErrorCode.INTEGRITY_FAILED);
            }
            digest.update(prefix);
            var buffer = new byte[8192];
            long total = prefix.length;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES) {
                    throw new BusinessException(DocumentErrorCode.INTEGRITY_FAILED);
                }
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256 算法", exception);
        }
    }

    private boolean matchesSignature(byte[] prefix, String mediaType) {
        return switch (mediaType) {
            case "application/pdf" -> startsWith(prefix, new int[]{0x25, 0x50, 0x44, 0x46, 0x2D});
            case "image/png" -> startsWith(prefix, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/jpeg" -> startsWith(prefix, new int[]{0xFF, 0xD8, 0xFF});
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation" ->
                    startsWith(prefix, new int[]{0x50, 0x4B, 0x03, 0x04});
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private BusinessException storageFailure(Exception cause) {
        LOGGER.error("对象存储操作失败", cause);
        return new BusinessException(DocumentErrorCode.STORAGE_UNAVAILABLE);
    }
}
