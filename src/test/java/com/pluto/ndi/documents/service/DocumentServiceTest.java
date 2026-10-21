package com.pluto.ndi.documents.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.documents.config.StorageProperties;
import com.pluto.ndi.documents.domain.DocumentErrorCode;
import com.pluto.ndi.documents.domain.DocumentStatus;
import com.pluto.ndi.documents.domain.entity.DocumentEntity;
import com.pluto.ndi.documents.repository.DocumentRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DocumentServiceTest {

    private final DocumentRepository repository = mock(DocumentRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T06:00:00Z"), ZoneOffset.UTC);
    private final DocumentService service = new DocumentService(repository, storage,
            new StorageProperties("http://localhost:9000", "http://localhost:9000", "key", "secret", "ndi-documents", true),
            clock);

    @Test
    void completesOnlyUploaderOwnedDocumentAndMakesItUsable() {
        var projectId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        when(storage.signUpload(eq("ndi-documents"), any())).thenReturn("http://upload.example/test");
        var intent = service.beginUpload("PROJECT", projectId, actorId, new UploadCommand(
                "CUSTOMER_SOLUTION", "方案.pdf", "application/pdf", 8, "a".repeat(64)));
        var captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(repository).save(captor.capture());
        var document = captor.getValue();
        assertThat(intent.documentId()).isEqualTo(document.id());
        assertThat(intent.requiredHeaders()).containsEntry("Content-Type", "application/pdf");
        assertThat(document.status()).isEqualTo(DocumentStatus.PENDING);

        when(repository.findLockedById(document.id())).thenReturn(Optional.of(document));
        var outsider = catchThrowable(() -> service.completeUpload("PROJECT", projectId, UUID.randomUUID(), document.id()));
        assertThat(outsider).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) outsider).errorCode()).isEqualTo(DocumentErrorCode.ACCESS_FORBIDDEN);

        when(storage.sealAndVerify("ndi-documents", document.stagingObjectKey(), document.objectKey(),
                8, "application/pdf", "a".repeat(64))).thenReturn("etag-test");
        assertThat(service.completeUpload("PROJECT", projectId, actorId, document.id()).status())
                .isEqualTo(DocumentStatus.AVAILABLE);
        when(repository.findById(document.id())).thenReturn(Optional.of(document));
        assertThat(service.isUsable("PROJECT", projectId, document.id(), "CUSTOMER_SOLUTION")).isTrue();
        assertThat(service.isUsable("PROJECT", UUID.randomUUID(), document.id(), "CUSTOMER_SOLUTION")).isFalse();
        assertThat(service.isUsable("PROJECT", projectId, document.id(), "AWARD_NOTICE")).isFalse();
    }

    @Test
    void rejectsUnsupportedOrOversizedUploadBeforeAccessingStorage() {
        var exception = catchThrowable(() -> service.beginUpload("PROJECT", UUID.randomUUID(), UUID.randomUUID(),
                new UploadCommand("GENERAL", "script.exe", "application/x-msdownload", 10, "a".repeat(64))));
        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) exception).errorCode()).isEqualTo(DocumentErrorCode.INVALID_UPLOAD);
    }

    @Test
    void softDeletePreventsReadAndUploadCompletion() {
        var projectId = UUID.randomUUID();
        var actorId = UUID.randomUUID();
        var document = DocumentEntity.pending(UUID.randomUUID(), "PROJECT", projectId, "GENERAL",
                "ndi-documents", "staging/test", "objects/test", "资料.pdf", "application/pdf",
                3, "a".repeat(64), actorId, Instant.now(clock));
        document.markAvailable("etag", Instant.now(clock));
        when(repository.findLockedById(document.id())).thenReturn(Optional.of(document));
        when(repository.findById(document.id())).thenReturn(Optional.of(document));

        var wrongScope = catchThrowable(() -> service.delete("PROJECT", UUID.randomUUID(), document.id()));
        assertThat(((BusinessException) wrongScope).errorCode()).isEqualTo(DocumentErrorCode.ACCESS_FORBIDDEN);
        assertThat(document.status()).isEqualTo(DocumentStatus.AVAILABLE);

        service.delete("PROJECT", projectId, document.id());
        assertThat(document.status()).isEqualTo(DocumentStatus.DELETED);
        assertThat(service.isUsable("PROJECT", projectId, document.id(), "GENERAL")).isFalse();
        var download = catchThrowable(() -> service.download("PROJECT", projectId, document.id()));
        assertThat(((BusinessException) download).errorCode()).isEqualTo(DocumentErrorCode.NOT_FOUND);
        var completion = catchThrowable(() -> service.completeUpload("PROJECT", projectId, actorId, document.id()));
        assertThat(((BusinessException) completion).errorCode()).isEqualTo(DocumentErrorCode.NOT_FOUND);
    }
}
