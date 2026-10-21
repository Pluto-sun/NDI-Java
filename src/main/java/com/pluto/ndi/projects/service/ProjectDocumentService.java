package com.pluto.ndi.projects.service;

import java.util.List;
import java.util.UUID;

import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.documents.service.DocumentSummary;
import com.pluto.ndi.documents.service.DownloadIntent;
import com.pluto.ndi.documents.service.UploadCommand;
import com.pluto.ndi.documents.service.UploadIntent;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ProjectDocumentPurpose;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在项目数据范围内调用文档模块，不向文档模块传递项目角色代码。 */
@Service
public class ProjectDocumentService {

    private final IdentityDirectory identityDirectory;
    private final ProjectRepository projectRepository;
    private final DocumentDirectory documentDirectory;
    private final ProjectApprovalRequestRepository approvalRepository;
    private final ProjectAccessService accessService;

    public ProjectDocumentService(
            IdentityDirectory identityDirectory, ProjectRepository projectRepository,
            DocumentDirectory documentDirectory, ProjectApprovalRequestRepository approvalRepository,
            ProjectAccessService accessService
    ) {
        this.identityDirectory = identityDirectory;
        this.projectRepository = projectRepository;
        this.documentDirectory = documentDirectory;
        this.approvalRepository = approvalRepository;
        this.accessService = accessService;
    }

    @Transactional
    public UploadIntent beginUpload(UUID actorId, UUID projectId, ProjectDocumentPurpose purpose, UploadCommand command) {
        requireScope(actorId, projectId, true);
        return documentDirectory.beginUpload("PROJECT", projectId, actorId,
                new UploadCommand(purpose.name(), command.originalName(), command.mediaType(),
                        command.byteSize(), command.sha256Hex()));
    }

    @Transactional
    public DocumentSummary completeUpload(UUID actorId, UUID projectId, UUID documentId) {
        requireScope(actorId, projectId, true);
        return documentDirectory.completeUpload("PROJECT", projectId, actorId, documentId);
    }

    @Transactional(readOnly = true)
    public DownloadIntent download(UUID actorId, UUID projectId, UUID documentId) {
        requireScope(actorId, projectId, false);
        return documentDirectory.download("PROJECT", projectId, documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> listAvailable(UUID actorId, UUID projectId) {
        requireScope(actorId, projectId, false);
        return documentDirectory.listAvailable("PROJECT", projectId);
    }

    @Transactional
    public void delete(UUID actorId, UUID projectId, UUID documentId) {
        requireScope(actorId, projectId, true);
        // 先由文档模块校验归属，再检查审批引用；引用检查失败时事务回滚状态变更。
        documentDirectory.delete("PROJECT", projectId, documentId);
        if (approvalRepository.existsByEvidenceDocumentId(documentId)) {
            throw new BusinessException(ProjectErrorCode.DOCUMENT_IN_USE);
        }
    }

    private void requireScope(UUID actorId, UUID projectId, boolean writing) {
        if (!writing) {
            accessService.requireReadable(actorId, projectId);
            return;
        }
        var project = projectRepository.findLockedById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
        var actor = identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        if (!identityDirectory.canUploadProjectDocument(actor, project.provinceId(), project.cityId())) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        if (ProjectStage.TERMINATED.name().equals(project.currentStageCode())) {
            throw new BusinessException(ProjectErrorCode.TERMINATED_READ_ONLY);
        }
    }
}
