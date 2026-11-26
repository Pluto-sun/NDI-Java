package com.pluto.ndi.projects.service;

import com.pluto.ndi.projects.ProjectRecordDeletion;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectRecordDeletionService implements ProjectRecordDeletion {
    private final ProjectRepository projects;
    private final ProjectApprovalRequestRepository approvals;
    private final AuditWriter audit;
    private final Clock clock;

    public ProjectRecordDeletionService(ProjectRepository projects, ProjectApprovalRequestRepository approvals,
            AuditWriter audit, Clock clock) {
        this.projects = projects;
        this.approvals = approvals;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public boolean deleteUnlessPending(UUID actor, UUID id, long version) {
        var project = projects.findLockedById(id)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
        if (approvals.findLockedByProjectIdAndStatus(id, ApprovalStatus.PENDING).isPresent()) return false;
        if (project.versionNo() != version) throw new BusinessException(ProjectErrorCode.VERSION_CONFLICT);
        project.markDeleted(actor, clock.instant());
        audit.record(actor, "PROJECT", id, "PROJECT_DELETED",
                Map.of("projectCode", project.projectCode(), "version", version));
        return true;
    }
}
