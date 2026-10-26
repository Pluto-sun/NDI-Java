package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectNarrative;
import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.util.ProjectExpertNames;
import com.pluto.ndi.projects.util.ProjectPlanningFields;
import com.pluto.ndi.projects.util.ProjectNarrativeText;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 项目普通信息维护；阶段、名称和金额不经此入口修改。 */
@Service
public class ProjectProfileService {

    private final ProjectAccessService accessService;
    private final IdentityDirectory identityDirectory;
    private final ProjectIndustryReferenceRepository industryRepository;
    private final ProjectRepository projectRepository;
    private final ProjectChangeEventRepository eventRepository;
    private final ProjectService projectService;
    private final ProjectMonthlySnapshotService monthlySnapshots;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ProjectInactivityService inactivity;
    private final ProjectResponsiblePersonStore people;

    public ProjectProfileService(
            ProjectAccessService accessService, IdentityDirectory identityDirectory,
            ProjectIndustryReferenceRepository industryRepository, ProjectRepository projectRepository,
            ProjectChangeEventRepository eventRepository, ProjectService projectService,
            ProjectMonthlySnapshotService monthlySnapshots, ObjectMapper objectMapper, Clock clock,
            ProjectInactivityService inactivity, ProjectResponsiblePersonStore people
    ) {
        this.accessService = accessService;
        this.identityDirectory = identityDirectory;
        this.industryRepository = industryRepository;
        this.projectRepository = projectRepository;
        this.eventRepository = eventRepository;
        this.projectService = projectService;
        this.monthlySnapshots = monthlySnapshots;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.inactivity = inactivity;
        this.people=people;
    }

    @Transactional
    public ProjectDetailSummary update(UUID actorId, UUID projectId, UpdateProjectProfileCommand command) {
        var project = accessService.requireProfileEditable(actorId, projectId);
        if (command == null || command.expectedVersion() < 0) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROFILE);
        }
        if (project.versionNo() != command.expectedVersion()) {
            throw new BusinessException(ProjectErrorCode.VERSION_CONFLICT);
        }
        var customerName = requiredText(command.customerName(), 200);
        var industryCode = requiredText(command.industryCode(), 3).toUpperCase(Locale.ROOT);
        var customerNeed = requiredText(command.customerNeed(), 10_000);
        var managerName = command.cityAccountManagerName()==null ? project.cityAccountManagerName() : requiredText(command.cityAccountManagerName(), 100);
        if (!industryRepository.existsByIndustryCodeAndEnabledTrue(industryCode)) {
            throw new BusinessException(ProjectErrorCode.INVALID_INDUSTRY);
        }
        if (command.provinceLeaderUserId() != null && !identityDirectory.isEligibleProvinceLeader(
                command.provinceLeaderUserId(), project.provinceId())) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROVINCE_LEADER);
        }
        var expertNames = project.productSupportExpertNames();
        if (command.productSupportExpertNames() != null) {
            try {
                expertNames = ProjectExpertNames.normalize(command.productSupportExpertNames());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ProjectErrorCode.INVALID_SUPPORT_EXPERTS);
            }
        }
        var scenarios = project.applicationScenarioCodes();
        var bidMonth = project.plannedBidMonth();
        try {
            if (command.applicationScenarioCodes() != null) {
                scenarios = ProjectPlanningFields.normalizeScenarios(command.applicationScenarioCodes());
            }
            if (command.plannedBidMonth() != null) {
                bidMonth = ProjectPlanningFields.parseBidMonth(command.plannedBidMonth());
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ProjectErrorCode.INVALID_PLANNING_FIELDS);
        }
        ProjectNarrative narrative = new ProjectNarrative(project.constructionContentRaw(),
                project.productPromotionContent(), project.remark());
        if (command.narrative() != null) {
            try {
                narrative = ProjectNarrativeText.normalize(command.narrative(), customerNeed);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ProjectErrorCode.INVALID_NARRATIVE);
            }
        }
        var before = snapshot(project);
        monthlySnapshots.ensureBaseline(project);
        var occurredAt = Instant.now(clock);
        if (project.updateProfile(customerName, industryCode, customerNeed,
                command.provinceLeaderUserId()==null ? project.provinceLeaderUserId() : command.provinceLeaderUserId(), managerName, expertNames, bidMonth, scenarios, narrative,
                actorId, occurredAt)) {
            projectRepository.saveAndFlush(project);
            if (command.provinceLeaderUserId()!=null || command.cityAccountManagerName()!=null || command.productSupportExpertNames()!=null)
                people.synchronizeLegacy(project);
            var event = ProjectChangeEventEntity.approvedChange(
                    projectId, project.versionNo(), "PROJECT_PROFILE_UPDATED",
                    eventPayload(before, snapshot(project)), actorId, occurredAt);
            eventRepository.save(event);
            monthlySnapshots.recordProfileIndustry(project, event.id(), (String) before.get("industryCode"));
            inactivity.resolveAfterActivity(project, actorId, occurredAt);
        }
        return projectService.findDetail(actorId, projectId);
    }

    private String requiredText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROFILE);
        }
        return value.trim();
    }

    private Map<String, Object> snapshot(ProjectEntity project) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("customerName", project.customerName());
        snapshot.put("industryCode", project.industryCode());
        snapshot.put("customerNeed", project.customerNeed());
        snapshot.put("provinceLeaderUserId", project.provinceLeaderUserId());
        snapshot.put("cityAccountManagerName", project.cityAccountManagerName());
        snapshot.put("productSupportExpertNames", project.productSupportExpertNames());
        snapshot.put("plannedBidMonth", project.plannedBidMonth());
        snapshot.put("applicationScenarioCodes", project.applicationScenarioCodes());
        snapshot.put("constructionContentRaw", project.constructionContentRaw());
        snapshot.put("productPromotionContent", project.productPromotionContent());
        snapshot.put("remark", project.remark());
        return snapshot;
    }

    private String eventPayload(Map<String, Object> before, Map<String, Object> after) {
        try {
            return objectMapper.writeValueAsString(Map.of("before", before, "after", after));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目普通信息变更事件无法序列化", exception);
        }
    }
}
