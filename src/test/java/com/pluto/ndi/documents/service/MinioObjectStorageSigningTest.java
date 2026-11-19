package com.pluto.ndi.documents.service;

import com.pluto.ndi.documents.config.StorageProperties;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class MinioObjectStorageSigningTest {

    @Test
    void signsUploadAndDownloadWithoutConnectingToPublicEndpoint() throws Exception {
        // 只绑定端口，不监听连接，模拟 Java 无法访问浏览器入口。
        try (var socket = new java.net.Socket()) {
            socket.bind(new java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            int port = socket.getLocalPort();
            var storage = new MinioObjectStorage(new StorageProperties(
                    "http://127.0.0.1:9000", "http://127.0.0.1:" + port,
                    "test-access", "test-secret", "ndi-documents", false, " us-east-1 "));

            var upload = URI.create(storage.signUpload("ndi-documents", "staging/upload"));
            var download = URI.create(storage.signDownload("ndi-documents", "objects/download"));

            assertThat(upload.getPath()).isEqualTo("/ndi-documents/staging/upload");
            assertThat(download.getPath()).isEqualTo("/ndi-documents/objects/download");
            for (var url : java.util.List.of(upload, download)) {
                assertThat(url.getPort()).isEqualTo(port);
                assertThat(url.getQuery()).contains("X-Amz-Signature=", "/us-east-1/s3/aws4_request");
            }
        }
    }
}
