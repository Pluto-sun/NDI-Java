package com.pluto.ndi.projects.service;

import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;

/** 项目单项读取与追加式协作的权限边界。 */
@Service
public class ProjectAccessService {

    private final IdentityDirectory identityDirectory;
    private final ProjectRepository projectRepository;

    public ProjectAccessService(IdentityDirectory identityDirectory, ProjectRepository projectRepository) {
        this.identityDirectory = identityDirectory;
        this.projectRepository = projectRepository;
    }

    public ProjectEntity requireReadable(UUID actorId, UUID projectId) {
        var project = requireProject(projectId);
        var actor = requireActor(actorId);
        var scope = identityDirectory.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        boolean inScope = switch (scope.level()) {
            case PROVINCE -> scope.organizationId().equals(project.provinceId());
            case CITY -> scope.organizationId().equals(project.cityId());
        };
        if (!inScope) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        return project;
    }

    public ProjectEntity requireAppendable(UUID actorId, UUID projectId) {
        var project = projectRepository.findLockedById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
        var actor = requireActor(actorId);
        if (!identityDirectory.canAppendProjectProgress(actor, project.provinceId(), project.cityId())) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        if (ProjectStage.TERMINATED.name().equals(project.currentStageCode())) {
            throw new BusinessException(ProjectErrorCode.TERMINATED_READ_ONLY);
        }
        return project;
    }

    private ProjectEntity requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
    }

    private ProjectActor requireActor(UUID actorId) {
        return identityDirectory.findProjectActor(actorId)
                .filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }
}
