package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.mapper.ProjectApprovalSpecifications;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 按当前账号查询待审批与已发起的申请。 */
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
        requireEnabled(actorId);
        var now = Instant.now(clock);
        var scope = identityDirectory.findApprovalScope(actorId);
        if (scope.empty()) return Page.empty(pageable);
        return repository.findAll(pending(actorId, scope, null), org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(), pageable.getPageSize(), org.springframework.data.domain.Sort.by("dueAt", "id")))
                .map(request -> ApprovalTaskSummary.from(request, now));
    }

    @Transactional(readOnly = true)
    public Page<ApprovalSummary> findSubmitted(UUID actorId, Pageable pageable) {
        var actor = requireEnabled(actorId);
        var scope = identityDirectory.findReadableProjectScope(actor);
        if (scope.isEmpty()) return Page.empty(pageable);
        return repository.findAll(ProjectApprovalSpecifications.readable(scope.get()).and(
                (request, query, builder) -> builder.equal(request.get("initiatorId"), actorId)), ordered(pageable))
                .map(ApprovalSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalSummary> findRecords(UUID actorId, ApprovalStatus status, Pageable pageable) {
        var actor = requireEnabled(actorId);
        var scope = identityDirectory.findReadableProjectScope(actor);
        if (scope.isEmpty()) return Page.empty(pageable);
        var specification = ProjectApprovalSpecifications.readable(scope.get());
        if (status != null) specification = specification.and(
                (request, query, builder) -> builder.equal(request.get("status"), status));
        return repository.findAll(specification, ordered(pageable)).map(ApprovalSummary::from);
    }

    private Pageable ordered(Pageable page) {
        return org.springframework.data.domain.PageRequest.of(page.getPageNumber(), page.getPageSize(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "submittedAt", "id"));
    }

    @Transactional(readOnly = true)
    public ApprovalInboxCounts counts(UUID actorId) {
        requireEnabled(actorId);
        var now = Instant.now(clock);
        var scope = identityDirectory.findApprovalScope(actorId);
        if (scope.empty()) return new ApprovalInboxCounts(0, 0);
        return new ApprovalInboxCounts(
                repository.count(pending(actorId, scope, null)),
                repository.count(pending(actorId, scope, now)));
    }

    private org.springframework.data.jpa.domain.Specification<com.pluto.ndi.projects.domain.entity.ProjectApprovalRequestEntity> pending(
            UUID actorId, com.pluto.ndi.identity.service.ProjectApprovalScope scope, Instant dueAt) {
        return (request, query, builder) -> {
            var project = query.from(com.pluto.ndi.projects.domain.entity.ProjectEntity.class);
            var interfaceProvince = project.get("provinceId").in(scope.interfaceProvinceIds());
            var leaderProvince = project.get("provinceId").in(scope.provinceLeaderProvinceIds());
            var eligible = builder.or(
                    builder.and(builder.equal(request.get("requestType"), "STAGE_ADVANCEMENT"), interfaceProvince),
                    builder.and(builder.equal(request.get("requestType"), "MAJOR_CHANGE"),
                            builder.equal(request.get("initiatorLevel"), com.pluto.ndi.identity.service.ProjectInitiatorLevel.PROVINCE), interfaceProvince),
                    builder.and(builder.equal(request.get("requestType"), "MAJOR_CHANGE"),
                            builder.equal(request.get("initiatorLevel"), com.pluto.ndi.identity.service.ProjectInitiatorLevel.CITY), leaderProvince));
            var base = builder.and(builder.equal(project.get("id"), request.get("projectId")),
                    builder.equal(request.get("approverId"), actorId), builder.equal(request.get("status"), ApprovalStatus.PENDING),
                    eligible);
            return dueAt == null ? base : builder.and(base, builder.lessThanOrEqualTo(request.get("dueAt"), dueAt));
        };
    }

    private ProjectActor requireEnabled(UUID actorId) {
        return identityDirectory.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }
}
