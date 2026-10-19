package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只列出当前账号被指定处理的待审申请。 */
@Service
public class ProjectApprovalInboxService {

    private final IdentityDirectory identityDirectory;
    private final ProjectApprovalRequestRepository repository;
    private final Clock clock;

    public ProjectApprovalInboxService(
            IdentityDirectory identityDirectory, ProjectApprovalRequestRepository repository, Clock clock
    ) {
        this.identityDirectory = identityDirectory;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ApprovalTaskSummary> findPending(UUID actorId, Pageable pageable) {
        identityDirectory.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var now = Instant.now(clock);
        return repository.findByApproverIdAndStatusOrderByDueAtAsc(actorId, ApprovalStatus.PENDING, pageable)
                .map(request -> ApprovalTaskSummary.from(request, now));
    }
}
