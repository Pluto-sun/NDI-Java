package com.pluto.ndi.projects.service;

import java.util.UUID;
import com.pluto.ndi.projects.domain.ProjectOperationAudit;
import com.pluto.ndi.projects.mapper.ProjectOperationAuditMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 鉴权后才读取项目事实；与督办链路审计分别展示，不重复展开自动通知。 */
@Service
public class ProjectOperationAuditService {
    private final ProjectAccessService access;
    private final ProjectOperationAuditMapper mapper;
    public ProjectOperationAuditService(ProjectAccessService access, ProjectOperationAuditMapper mapper) {
        this.access = access;
        this.mapper = mapper;
    }
    @Transactional(readOnly = true)
    public Page<ProjectOperationAudit> find(UUID actorId, UUID projectId, Pageable page) {
        access.requireReadable(actorId, projectId);
        return mapper.find(projectId, page);
    }
}
