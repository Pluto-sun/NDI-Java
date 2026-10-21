package com.pluto.ndi.documents.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.pluto.ndi.documents.config.StorageProperties;
import com.pluto.ndi.documents.domain.DocumentErrorCode;
import com.pluto.ndi.documents.domain.DocumentStatus;
import com.pluto.ndi.documents.domain.entity.DocumentEntity;
import com.pluto.ndi.documents.repository.DocumentRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 文档元数据与对象存储之间的上传闭环。 */
@Service
class DocumentService implements DocumentDirectory {

    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "image/png",
            "image/jpeg"
    );

    private final DocumentRepository repository;
    private final ObjectStorage storage;
    private final StorageProperties properties;
    private final Clock clock;

    DocumentService(DocumentRepository repository, ObjectStorage storage, StorageProperties properties, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UploadIntent beginUpload(String scopeType, UUID scopeId, UUID actorId, UploadCommand command) {
        validate(scopeType, scopeId, actorId, command);
        var id = UUID.randomUUID();
        var stagingKey = "staging/" + id;
        var objectKey = "objects/" + id;
        storage.ensureBucket(properties.bucket());
        var uploadUrl = storage.signUpload(properties.bucket(), stagingKey);
        var now = Instant.now(clock);
        repository.save(DocumentEntity.pending(
                id, scopeType, scopeId, command.purposeCode(), properties.bucket(),
                stagingKey, objectKey, command.originalName().trim(), command.mediaType(),
                command.byteSize(), command.sha256Hex().toLowerCase(Locale.ROOT), actorId, now
        ));
        return new UploadIntent(id, "PUT", uploadUrl,
                Map.of("Content-Type", command.mediaType()), now.plusSeconds(600));
    }

    @Override
    @Transactional
    public DocumentSummary completeUpload(String scopeType, UUID scopeId, UUID actorId, UUID documentId) {
        var document = repository.findLockedById(documentId)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.NOT_FOUND));
        requireScope(document, scopeType, scopeId);
        if (!actorId.equals(document.uploadedBy())) {
            throw new BusinessException(DocumentErrorCode.ACCESS_FORBIDDEN);
        }
        if (document.status() == DocumentStatus.AVAILABLE) {
            return DocumentSummary.from(document);
        }
        if (document.status() == DocumentStatus.DELETED) {
            throw new BusinessException(DocumentErrorCode.NOT_FOUND);
        }
        var etag = storage.sealAndVerify(
                document.bucketName(), document.stagingObjectKey(), document.objectKey(),
                document.byteSize(), document.mediaType(), document.sha256Hex());
        document.markAvailable(etag, Instant.now(clock));
        return DocumentSummary.from(document);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsable(String scopeType, UUID scopeId, UUID documentId, String purposeCode) {
        if (documentId == null) {
            return false;
        }
        return repository.findById(documentId)
                .filter(document -> document.status() == DocumentStatus.AVAILABLE)
                .filter(document -> scopeType.equals(document.scopeType()))
                .filter(document -> scopeId.equals(document.scopeId()))
                .filter(document -> purposeCode.equals(document.purposeCode()))
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadIntent download(String scopeType, UUID scopeId, UUID documentId) {
        var document = repository.findById(documentId)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.NOT_FOUND));
        requireScope(document, scopeType, scopeId);
        if (document.status() != DocumentStatus.AVAILABLE) {
            throw new BusinessException(DocumentErrorCode.NOT_AVAILABLE);
        }
        return new DownloadIntent(
                storage.signDownload(document.bucketName(), document.objectKey()),
                Instant.now(clock).plusSeconds(300)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentSummary> listAvailable(String scopeType, UUID scopeId) {
        return repository.findByScopeTypeAndScopeIdAndStatusOrderByCreatedAtDesc(
                        scopeType, scopeId, DocumentStatus.AVAILABLE)
                .stream().map(DocumentSummary::from).toList();
    }

    @Override
    @Transactional
    public void delete(String scopeType, UUID scopeId, UUID documentId) {
        var document = repository.findLockedById(documentId)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.NOT_FOUND));
        requireScope(document, scopeType, scopeId);
        if (document.status() == DocumentStatus.DELETED) {
            throw new BusinessException(DocumentErrorCode.NOT_FOUND);
        }
        document.markDeleted();
    }

    private void validate(String scopeType, UUID scopeId, UUID actorId, UploadCommand command) {
        if (scopeType == null || scopeType.isBlank() || scopeType.length() > 40 || scopeId == null || actorId == null
                || command == null || command.purposeCode() == null || command.purposeCode().isBlank()
                || command.purposeCode().length() > 40 || command.originalName() == null
                || command.originalName().isBlank() || command.originalName().length() > 255
                || command.originalName().contains("/") || command.originalName().contains("\\")
                || command.originalName().chars().anyMatch(Character::isISOControl)
                || command.mediaType() == null || !ALLOWED_MEDIA_TYPES.contains(command.mediaType())
                || command.byteSize() < 1 || command.byteSize() > MAX_FILE_BYTES
                || command.sha256Hex() == null
                || !SHA256.matcher(command.sha256Hex().toLowerCase(Locale.ROOT)).matches()) {
            throw new BusinessException(DocumentErrorCode.INVALID_UPLOAD);
        }
    }

    private void requireScope(DocumentEntity document, String scopeType, UUID scopeId) {
        if (!document.scopeType().equals(scopeType) || !document.scopeId().equals(scopeId)) {
            throw new BusinessException(DocumentErrorCode.ACCESS_FORBIDDEN);
        }
    }
}
