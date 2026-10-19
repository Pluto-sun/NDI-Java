package com.pluto.ndi.projects.service;

import java.util.UUID;
import java.util.List;

import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.documents.service.DocumentSummary;
import com.pluto.ndi.documents.service.DownloadIntent;
import com.pluto.ndi.documents.service.UploadCommand;
import com.pluto.ndi.documents.service.UploadIntent;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ProjectDocumentPurpose;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;

/** 在项目数据范围内调用文档模块，不向文档模块传递项目角色代码。 */
@Service
public class ProjectDocumentService {

    private final IdentityDirectory identityDirectory;
    private final ProjectRepository projectRepository;
    private final DocumentDirectory documentDirectory;

    public ProjectDocumentService(
            IdentityDirectory identityDirectory, ProjectRepository projectRepository,
            DocumentDirectory documentDirectory
    ) {
        this.identityDirectory = identityDirectory;
        this.projectRepository = projectRepository;
        this.documentDirectory = documentDirectory;
    }

    public UploadIntent beginUpload(UUID actorId, UUID projectId, ProjectDocumentPurpose purpose, UploadCommand command) {
        requireScope(actorId, projectId);
        return documentDirectory.beginUpload("PROJECT", projectId, actorId,
                new UploadCommand(purpose.name(), command.originalName(), command.mediaType(),
                        command.byteSize(), command.sha256Hex()));
    }

    public DocumentSummary completeUpload(UUID actorId, UUID projectId, UUID documentId) {
        requireScope(actorId, projectId);
        return documentDirectory.completeUpload("PROJECT", projectId, actorId, documentId);
    }

    public DownloadIntent download(UUID actorId, UUID projectId, UUID documentId) {
        requireScope(actorId, projectId);
        return documentDirectory.download("PROJECT", projectId, documentId);
    }

    public List<DocumentSummary> listAvailable(UUID actorId, UUID projectId) {
        requireScope(actorId, projectId);
        return documentDirectory.listAvailable("PROJECT", projectId);
    }

    private ProjectEntity requireScope(UUID actorId, UUID projectId) {
        var project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
        var actor = identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        if (!identityDirectory.canUploadProjectDocument(actor, project.provinceId(), project.cityId())) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return project;
    }
}
