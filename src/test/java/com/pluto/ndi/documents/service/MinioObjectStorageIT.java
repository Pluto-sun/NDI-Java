package com.pluto.ndi.documents.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import com.pluto.ndi.documents.config.StorageProperties;
import com.pluto.ndi.documents.domain.DocumentErrorCode;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class MinioObjectStorageIT {

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "netiedge/minio:RELEASE.2025-01-20T14-49-07Z@sha256:e6c282005f6c8496256f8fd07e9a8b82073344c217ea87436d9af888db67eb0e"))
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "ndi_minio_test")
            .withEnv("MINIO_ROOT_PASSWORD", "ndi_minio_test_password")
            .withCommand("server", "/data", "--console-address", ":9001")
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @Test
    void sealsVerifiedObjectAndIgnoresLaterStagingOverwrite() throws Exception {
        var endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        var storage = new MinioObjectStorage(new StorageProperties(
                endpoint, endpoint, "ndi_minio_test", "ndi_minio_test_password", "ndi-it-documents", true));
        var http = HttpClient.newHttpClient();
        var key = UUID.randomUUID().toString();
        var stagingKey = "staging/" + key;
        var objectKey = "objects/" + key;
        var original = "%PDF-1.7\n测试文件".getBytes(StandardCharsets.UTF_8);
        var expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));

        storage.ensureBucket("ndi-it-documents");
        var uploadUrl = storage.signUpload("ndi-it-documents", stagingKey);
        var upload = HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", "application/pdf")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(original)).build();
        assertThat(http.send(upload, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);

        assertThat(storage.sealAndVerify("ndi-it-documents", stagingKey, objectKey,
                original.length, "application/pdf", expectedHash)).isNotBlank();
        var downloadUrl = storage.signDownload("ndi-it-documents", objectKey);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body()).isEqualTo(original);

        var overwritten = HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", "application/pdf")
                .PUT(HttpRequest.BodyPublishers.ofString("替换内容")).build();
        assertThat(http.send(overwritten, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        var replacement = "替换内容".getBytes(StandardCharsets.UTF_8);
        var replacementHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(replacement));
        var invalidContent = catchThrowable(() -> storage.sealAndVerify(
                "ndi-it-documents", stagingKey, "objects/invalid-" + key,
                replacement.length, "application/pdf", replacementHash));
        assertThat(invalidContent).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) invalidContent).errorCode()).isEqualTo(DocumentErrorCode.INTEGRITY_FAILED);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body()).isEqualTo(original);
    }
}
