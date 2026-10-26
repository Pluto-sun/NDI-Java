package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectReminderAudience;
import com.pluto.ndi.identity.service.ProjectReminderForwarding;
import com.pluto.ndi.identity.service.ProjectUser;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectInactivityCase;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.mapper.ProjectInactivityMapper;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 督办周期与项目变更共用项目行锁，避免升级、转发和闭环互相穿透。 */
@Service
public class ProjectInactivityService {
    private final ProjectRepository projects;
    private final ProjectInactivityMapper mapper;
    private final ProjectAccessService access;
    private final IdentityDirectory identities;
    private final Clock clock;
    private final ProjectResponsiblePersonStore people;

    public ProjectInactivityService(ProjectRepository projects, ProjectInactivityMapper mapper,
            ProjectAccessService access, IdentityDirectory identities, Clock clock,ProjectResponsiblePersonStore people) {
        this.projects = projects;
        this.mapper = mapper;
        this.access = access;
        this.identities = identities;
        this.clock = clock;
        this.people=people;
    }

    public record ScanResult(UUID caseId, String audience, int recipients, boolean delivered, String status) { }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<ScanResult> recordIfDue(UUID projectId, Instant now) {
        var project = projects.findLockedById(projectId).orElse(null);
        if (project == null || ProjectStage.TERMINATED.name().equals(project.currentStageCode())) {
            return Optional.empty();
        }
        mapper.initialize(project);
        long days = Duration.between(mapper.activity(projectId, true).lastActivityAt(), now).toDays();
        return days < 30 ? Optional.empty() : Optional.of(record(project, days, now));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ScanResult trigger(UUID actorId, UUID projectId, int days) {
        if (!identities.isSystemAdministrator(actorId)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        if (days != 30 && days != 33 && days != 36) {
            throw new BusinessException(ProjectErrorCode.INACTIVITY_INVALID_TRIGGER);
        }
        var project = projects.findLockedById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
        if (ProjectStage.TERMINATED.name().equals(project.currentStageCode())) {
            throw new BusinessException(ProjectErrorCode.TERMINATED_READ_ONLY);
        }
        var now = Instant.now(clock);
        var result = record(project, days, now);
        mapper.audit(projectId, result.caseId(), actorId, "INACTIVITY_MANUAL_TRIGGER", days + "天督办演练", now);
        return result;
    }

    private ScanResult record(ProjectEntity project, long days, Instant now) {
        mapper.initialize(project);
        var activity = mapper.activity(project.id(), true);
        var existing = mapper.findCycle(project.id(), activity.revision());
        var caseId = existing.map(ProjectInactivityCase::id).orElseGet(() -> mapper.open(project, activity, now));
        var audience = days >= 36 ? ProjectReminderAudience.GROUP
                : days >= 33 ? ProjectReminderAudience.INTERFACE : ProjectReminderAudience.PROVINCE;
        UUID assigned=people.provinceAccount(project.id());
        var recipients=identities.findProjectReminderRecipients(project.provinceId(),assigned,audience);
        boolean redirected=audience==ProjectReminderAudience.PROVINCE && recipients.isEmpty();
        if (redirected) {
            audience=ProjectReminderAudience.INTERFACE;
            recipients=identities.findProjectReminderRecipients(project.provinceId(),assigned,audience);
        }
        final var targetAudience=audience;
        var sentAt = existing.map(item -> switch (targetAudience) {
            case PROVINCE -> item.provinceSentAt();
            case INTERFACE -> item.interfaceSentAt();
            case GROUP -> item.groupSentAt();
        }).orElse(null);
        if (sentAt != null || existing.filter(item -> !"OPEN".equals(item.status())).isPresent()) {
            return new ScanResult(caseId, audience.name(), 0, false, "ALREADY_HANDLED");
        }
        if (recipients.isEmpty()) {
            mapper.audit(project.id(),caseId,null,"INACTIVITY_NO_RECIPIENTS","省分及负责接口人无可用接收账号，需检查配置",now);
            return new ScanResult(caseId, audience.name(), 0, false, "NO_RECIPIENTS");
        }
        for (var recipient : recipients) mapper.notice(caseId, recipient.id(), audience.name(), now, false);
        mapper.escalated(caseId, audience, now);
        if (redirected) mapper.escalated(caseId,ProjectReminderAudience.PROVINCE,now);
        mapper.audit(project.id(), caseId, null, "INACTIVITY_REMINDER_" + audience.name(),
                "连续" + days + "天未变动，接收账号=" + recipients.stream().map(user -> user.id().toString()).toList()
                + (redirected ? "；省分无合格接收人，立即转交负责接口人" : ""), now);
        return new ScanResult(caseId, audience.name(), recipients.size(), true, "DELIVERED");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(ProjectEntity project, Instant lastActivityAt) {
        mapper.initialize(project, lastActivityAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void resolveAfterActivity(ProjectEntity project, UUID actorId, Instant at) {
        mapper.initialize(project);
        mapper.recordActivity(project.id(), at);
        mapper.close(project.id(), actorId, at);
    }

    @Transactional(readOnly = true)
    public Page<ProjectInactivitySummary> findMine(UUID actorId, boolean history, Pageable page) {
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var scope = identities.findReadableProjectScope(actor);
        if (scope.isEmpty()) return Page.empty(page);
        var cases = mapper.findMine(actorId, scope.get(), history, page);
        var unread = mapper.unreadCases(actorId, cases.stream().map(ProjectInactivityCase::id).toList());
        var dispatches = mapper.dispatchEvents(cases.stream().map(ProjectInactivityCase::id).toList());
        return cases.map(item -> {
            var events = new ArrayList<>(dispatches.getOrDefault(item.id(), List.of()));
            if (item.provinceSentAt() != null) events.add(new ProjectInactivityEvent("PROVINCE",
                    "系统发起30天未变动省分督办", item.provinceSentAt()));
            if (item.interfaceSentAt() != null) events.add(new ProjectInactivityEvent("INTERFACE",
                    "系统发起33天未变动接口人督办", item.interfaceSentAt()));
            if (item.groupSentAt() != null) events.add(new ProjectInactivityEvent("GROUP",
                    "系统发起36天未变动集团督办", item.groupSentAt()));
            if (item.closedAt() != null) events.add(new ProjectInactivityEvent("RESOLVED",
                    "项目发生有效更新，督办闭环", item.closedAt()));
            events.sort(Comparator.comparing(ProjectInactivityEvent::occurredAt));
            boolean canForward = !history && item.pending() && "OPEN".equals(item.status())
                    && identities.findProjectReminderForwarding(actorId, item.provinceId(), item.cityId()).isPresent();
            return new ProjectInactivitySummary(item.id(), item.projectId(), item.projectCode(), item.projectName(),
                    "RESOLVED".equals(item.status()) ? "RESOLVED" : history ? "FORWARDED" : "OPEN",
                    item.lastActivityAt(), item.createdAt(), item.closedAt(), canForward,
                    "OPEN".equals(item.status()) && unread.contains(item.id()), List.copyOf(events));
        });
    }

    @Transactional(readOnly = true)
    public long pendingCount(UUID actorId) {
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return identities.findReadableProjectScope(actor).map(scope -> mapper.pendingCount(actorId, scope)).orElse(0L);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID actorId) {
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        return identities.findReadableProjectScope(actor).map(scope -> mapper.unreadCount(actorId, scope)).orElse(0L);
    }

    /** 与转发共用项目锁；重复阅读幂等，重新转发后允许再次成为未读。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void markRead(UUID actorId, UUID caseId) {
        var item = findCase(caseId);
        projects.findLockedById(item.projectId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
        access.requireReadable(actorId, item.projectId());
        if (!mapper.hasNotice(caseId, actorId)) {
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        }
        mapper.markRead(caseId, actorId, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public Page<ProjectInactivityAudit> auditHistory(UUID actorId, UUID projectId, Pageable page) {
        access.requireReadable(actorId, projectId);
        return mapper.auditHistory(projectId, page);
    }

    @Transactional(readOnly = true)
    public List<ProjectUser> recipients(UUID actorId, UUID caseId) {
        var item = findCase(caseId);
        var project = access.requireReadable(actorId, item.projectId());
        requireActionable(item, project, mapper.hasPendingNotice(item.id(), actorId));
        return route(actorId, project).recipients().stream().filter(user -> !user.id().equals(actorId)).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void forward(UUID actorId, UUID caseId, UUID recipientId, String note) {
        var reference = findCase(caseId);
        var project = projects.findLockedById(reference.projectId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.NOT_FOUND));
        access.requireReadable(actorId, project.id());
        var item = mapper.findLocked(caseId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INACTIVITY_NOT_FOUND));
        requireActionable(item, project, mapper.lockPendingNotice(caseId, actorId));
        var route = route(actorId, project);
        if (recipientId == null || recipientId.equals(actorId) || note == null || note.isBlank()
                || note.trim().length() > 2000) {
            throw new BusinessException(ProjectErrorCode.INACTIVITY_INVALID_FORWARD);
        }
        var recipient = route.recipients().stream().filter(user -> user.id().equals(recipientId)).findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.INACTIVITY_INVALID_FORWARD));
        var sender = identities.findProjectUser(actorId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var now = Instant.now(clock);
        var handled = "INTERFACE_TO_PROVINCE".equals(route.route())
                ? identities.findProjectReminderRecipients(project.provinceId(), people.provinceAccount(project.id()),
                        ProjectReminderAudience.INTERFACE).stream().map(ProjectUser::id).toList()
                : List.of(actorId);
        mapper.handle(caseId, handled, now);
        mapper.dispatch(caseId, actorId, sender.displayName(), recipientId, recipient.displayName(),
                route.route(), note.trim(), now);
        mapper.notice(caseId, recipientId, "PROVINCE_TO_CITY".equals(route.route()) ? "CITY" : route.route(), now, true);
        mapper.audit(project.id(), caseId, actorId, "INACTIVITY_FORWARD", note.trim(), now);
    }

    private ProjectInactivityCase findCase(UUID caseId) {
        return mapper.find(caseId).orElseThrow(() -> new BusinessException(ProjectErrorCode.INACTIVITY_NOT_FOUND));
    }

    private void requireActionable(ProjectInactivityCase item, ProjectEntity project, boolean pending) {
        if (!"OPEN".equals(item.status()) || item.activityRevision() != mapper.activity(project.id(), false).revision()
                || ProjectStage.TERMINATED.name().equals(project.currentStageCode())
                || !pending) {
            throw new BusinessException(ProjectErrorCode.INACTIVITY_NOT_ACTIONABLE);
        }
    }

    private ProjectReminderForwarding route(UUID actorId, ProjectEntity project) {
        return identities.findProjectReminderForwarding(actorId, project.provinceId(), project.cityId())
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
    }
}
