package com.pluto.ndi.projects.service;

import java.util.UUID;

import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目内的审批申请与正式变更审计，仅对项目可读范围开放。 */
@Service
public class ProjectHistoryService {
    private final ProjectAccessService access;
    private final ProjectApprovalRequestRepository approvals;
    private final ProjectChangeEventRepository changes;

    public ProjectHistoryService(ProjectAccessService access, ProjectApprovalRequestRepository approvals,
            ProjectChangeEventRepository changes) {
        this.access = access;
        this.approvals = approvals;
        this.changes = changes;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalSummary> approvals(UUID actorId, UUID projectId, Pageable pageable) {
        access.requireReadable(actorId, projectId);
        return approvals.findByProjectIdOrderBySubmittedAtDescIdDesc(projectId, pageable)
                .map(ApprovalSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<ProjectChangeSummary> changes(UUID actorId, UUID projectId, Pageable pageable) {
        access.requireReadable(actorId, projectId);
        return changes.findByProjectIdOrderByOccurredAtDescIdDesc(projectId, pageable)
                .map(ProjectChangeSummary::from);
    }
}
