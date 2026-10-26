package com.pluto.ndi.projects.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.projects.domain.*;
import com.pluto.ndi.projects.domain.entity.ProjectChangeEventEntity;
import com.pluto.ndi.projects.repository.*;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/** 人员配置与项目普通资料共用版本号和项目行锁。 */
@Service
public class ProjectPeopleService {
    private final ProjectAccessService access;
    private final ProjectResponsiblePersonStore people;
    private final ProjectRepository projects;
    private final ProjectChangeEventRepository events;
    private final ProjectService service;
    private final ProjectInactivityService inactivity;
    private final IdentityDirectory identity;
    private final ObjectMapper json;
    private final Clock clock;

    public ProjectPeopleService(
            ProjectAccessService access,
            ProjectResponsiblePersonStore people,
            ProjectRepository projects,
            ProjectChangeEventRepository events,
            ProjectService service,
            ProjectInactivityService inactivity,
            IdentityDirectory identity,
            ObjectMapper json,
            Clock clock) {
        this.access = access;
        this.people = people;
        this.projects = projects;
        this.events = events;
        this.service = service;
        this.inactivity = inactivity;
        this.identity = identity;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ProjectDetailSummary configure(
            UUID actor, UUID id, long version, List<ProjectResponsiblePerson> input) {
        var project = access.requireProfileEditable(actor, id);
        if (version < 0 || version != project.versionNo())
            throw new BusinessException(ProjectErrorCode.VERSION_CONFLICT);
        var before = people.find(id);
        people.replace(project, input);
        var after = people.find(id);
        if (before.equals(after)) return service.findDetail(actor, id);
        var leader =
                after.stream()
                        .filter(p -> "PROVINCE_LEADER".equals(p.responsibility()))
                        .findFirst();
        var manager =
                after.stream()
                        .filter(p -> "CITY_MANAGER".equals(p.responsibility()))
                        .map(ProjectResponsiblePerson::name)
                        .findFirst()
                        .orElse("");
        var experts =
                after.stream()
                        .filter(p -> "SUPPORT_EXPERT".equals(p.responsibility()))
                        .map(ProjectResponsiblePerson::name)
                        .distinct()
                        .toList();
        project.replaceResponsibleSummary(
                leader.map(ProjectResponsiblePerson::userId).orElse(null), manager, experts);
        var at = Instant.now(clock);
        project.recordResponsiblePersonChange(actor, at);
        projects.saveAndFlush(project);
        try {
            events.save(
                    ProjectChangeEventEntity.approvedChange(
                            id,
                            project.versionNo(),
                            "PROJECT_PEOPLE_UPDATED",
                            json.writeValueAsString(Map.of("before", before, "after", after)),
                            actor,
                            at));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("项目人员审计序列化失败", error);
        }
        inactivity.resolveAfterActivity(project, actor, at);
        return service.findDetail(actor, id);
    }

    @Transactional(readOnly = true)
    public List<ProjectUser> accountOptions(
            UUID actor, UUID id, String keyword, boolean provinceOnly) {
        var project = access.requireReadable(actor, id);
        // 账号目录属于配置辅助信息，仍须具有本项目维护资格；终止项目不开放配置。
        var account =
                identity.findProjectActor(actor)
                        .filter(ProjectActor::enabled)
                        .orElseThrow(
                                () -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        if (!identity.canUpdateProjectProfile(account, project.provinceId(), project.cityId())
                || "TERMINATED".equals(project.currentStageCode()))
            throw new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN);
        return identity.searchProjectBindingAccounts(keyword, project.provinceId(), provinceOnly);
    }
}
