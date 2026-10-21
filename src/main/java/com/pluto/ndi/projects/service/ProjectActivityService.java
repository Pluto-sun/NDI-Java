package com.pluto.ndi.projects.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectActivitySourceRow;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import com.pluto.ndi.projects.repository.ProjectProgressRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 从已生效的主档事件和进展中读取动态，审批申请本身不进入工作台。 */
@Service
public class ProjectActivityService {

    private static final int MAX_ACTIVITIES = 500;

    private final IdentityDirectory identities;
    private final ProjectChangeEventRepository events;
    private final ProjectProgressRepository progresses;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProjectActivityService(IdentityDirectory identities, ProjectChangeEventRepository events,
            ProjectProgressRepository progresses, ObjectMapper objectMapper, Clock clock) {
        this.identities = identities;
        this.events = events;
        this.progresses = progresses;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProjectRecentActivity> recent(UUID actorId) {
        var actor = identities.findProjectActor(actorId).filter(ProjectActor::enabled)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var scope = identities.findReadableProjectScope(actor)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var since = Instant.now(clock).minus(30, ChronoUnit.DAYS);
        var page = PageRequest.of(0, MAX_ACTIVITIES);
        return Stream.concat(
                events.findRecentActivities(since, scope.level().name(), scope.organizationId(), scope.provinceIds(), page).stream(),
                progresses.findRecentActivities(since, scope.level().name(), scope.organizationId(), scope.provinceIds(), page).stream())
                .sorted(Comparator.comparing(ProjectActivitySourceRow::occurredAt).reversed()
                        .thenComparing(ProjectActivitySourceRow::id))
                .limit(MAX_ACTIVITIES)
                .map(row -> new ProjectRecentActivity(row.projectId(), row.projectCode(), row.projectName(),
                        row.province(), row.city(), summary(row), row.operatorName(), row.occurredAt()))
                .toList();
    }

    private String summary(ProjectActivitySourceRow row) {
        return switch (row.eventType()) {
            case "PROJECT_CREATED" -> "创建项目 " + row.projectCode();
            case "PROJECT_PROFILE_UPDATED" -> "更新项目资料";
            case "PROGRESS_APPENDED" -> "追加项目进展";
            case "PROGRESS_CORRECTED" -> "更正项目进展";
            case "STAGE_ADVANCEMENT_APPROVED" -> approvedStageSummary(row.payload(), "阶段推进审批通过");
            case "MAJOR_CHANGE_APPROVED" -> approvedStageSummary(row.payload(), "重大变更审批通过");
            default -> "项目资料已更新";
        };
    }

    private String approvedStageSummary(String payload, String fallback) {
        if (payload == null) return fallback;
        try {
            JsonNode root = objectMapper.readTree(payload);
            var before = root.path("before").path("stageCode").asText("");
            var after = root.path("after").path("stageCode").asText("");
            if (!after.isBlank() && !after.equals(before)) {
                return fallback + "：" + ProjectStage.valueOf(after).label();
            }
        } catch (Exception ignored) {
            // 旧事件载荷缺失时保留安全的通用摘要，不暴露原始 JSON。
        }
        return fallback;
    }
}
