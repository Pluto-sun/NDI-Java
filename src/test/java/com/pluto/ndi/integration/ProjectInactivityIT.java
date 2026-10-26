package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.mapper.ProjectInactivityMapper;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.service.AppendProgressCommand;
import com.pluto.ndi.projects.service.CreateProjectCommand;
import com.pluto.ndi.projects.service.MajorChangeCommand;
import com.pluto.ndi.projects.service.ProjectApprovalService;
import com.pluto.ndi.projects.service.ProjectInactivityService;
import com.pluto.ndi.projects.service.ProjectInactivityJob;
import com.pluto.ndi.projects.service.ProjectProfileService;
import com.pluto.ndi.projects.service.ProjectProgressService;
import com.pluto.ndi.projects.service.ProjectService;
import com.pluto.ndi.projects.service.UpdateProjectProfileCommand;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = NdiApplication.class, properties = {
        "ndi.projects.inactivity.enabled=false", "ndi.approvals.reminders-enabled=false",
        "ndi.projects.export.initial-delay-ms=600000"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProjectInactivityIT {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProjectService projects;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectInactivityService inactivity;
    @Autowired private ProjectInactivityMapper mapper;
    @Autowired private ProjectProgressService progresses;
    @Autowired private ProjectApprovalService approvals;
    @Autowired private ProjectProfileService profiles;
    @Autowired private MockMvc mvc;

    private final PageRequest page = PageRequest.of(0, 20);
    private UUID provinceId;
    private UUID cityId;
    private UUID cityUser;
    private UUID leader;
    private UUID reviewer;
    private UUID secondReviewer;
    private UUID group;
    private UUID administrator;

    @BeforeEach
    void seed() {
        jdbc.update("UPDATE identity_user_account SET enabled = false");
        jdbc.update("UPDATE project_activity_state SET last_activity_at = ?",
                LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        provinceId = UUID.randomUUID();
        cityId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province (id, province_code, province_name, enabled) VALUES (UUID_TO_BIN(?), ?, '督办测试省', true)",
                provinceId.toString(), provinceId.toString().replace("-", ""));
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '督办测试市', true)",
                cityId.toString(), provinceId.toString(), cityId.toString().replace("-", ""));
        cityUser = addUser("CITY_REPORTER", provinceId, cityId);
        leader = addUser("PROVINCE_REPORTER", provinceId, null);
        reviewer = addUser("INTERFACE_REVIEWER", provinceId, null);
        secondReviewer = addUser("INTERFACE_REVIEWER", null, null);
        group = addUser("GROUP_REVIEWER", null, null);
        administrator = addUser("SYSTEM_ADMIN", null, null);
    }

    @Test
    void readingOnlyChangesPersonalUnreadStateAndForwardingReopensIt() throws Exception {
        var project = create();
        var reminder = inactivity.trigger(administrator, project, 30);
        var before = projectRepository.findById(project).orElseThrow();
        var activity = mapper.activity(project, false);
        assertThat(inactivity.unreadCount(leader)).isEqualTo(1);
        mvc.perform(post("/api/v1/projects/inactivity-cases/{id}/read", reminder.caseId())
                .header("X-NDI-Actor-Id", leader.toString())).andExpect(status().isOk());
        inactivity.markRead(leader, reminder.caseId());
        assertThat(inactivity.unreadCount(leader)).isZero();
        assertThat(inactivity.pendingCount(leader)).isEqualTo(1);
        assertThat(inactivity.findMine(leader, false, page).getContent()).singleElement()
                .satisfies(item -> { assertThat(item.unread()).isFalse(); assertThat(item.canForward()).isTrue(); });
        assertThat(inactivity.findMine(leader, true, page)).isEmpty();
        assertThat(mapper.find(reminder.caseId()).orElseThrow().status()).isEqualTo("OPEN");
        assertThat(mapper.activity(project, false)).isEqualTo(activity);
        assertThat(projectRepository.findById(project).orElseThrow().versionNo()).isEqualTo(before.versionNo());
        assertThat(projectRepository.findById(project).orElseThrow().updatedAt()).isEqualTo(before.updatedAt());
        inactivity.trigger(administrator, project, 33);
        inactivity.markRead(reviewer, reminder.caseId());
        assertThat(inactivity.unreadCount(secondReviewer)).isZero();
        inactivity.forward(leader, reminder.caseId(), cityUser, "请跟进");
        inactivity.forward(reviewer, reminder.caseId(), leader, "再次提醒省分");
        assertThat(inactivity.unreadCount(leader)).isEqualTo(1);
        assertThat(inactivity.pendingCount(leader)).isEqualTo(1);
        mvc.perform(get("/api/v1/projects/notification-counts").header("X-NDI-Actor-Id", leader.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inactivityUnreadCount").value(1))
                .andExpect(jsonPath("$.data.inactivityPendingCount").value(1));
    }

    @Test
    void readingRequiresPersonalReceiptAndCurrentProjectScope() throws Exception {
        var project = create();
        var reminder = inactivity.trigger(administrator, project, 30);
        for (var actor : List.of(cityUser, administrator)) {
            mvc.perform(post("/api/v1/projects/inactivity-cases/{id}/read", reminder.caseId())
                    .header("X-NDI-Actor-Id", actor.toString())).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE identity_user_account SET enabled = false WHERE id = UUID_TO_BIN(?)", leader.toString());
        mvc.perform(post("/api/v1/projects/inactivity-cases/{id}/read", reminder.caseId())
                .header("X-NDI-Actor-Id", leader.toString())).andExpect(status().isForbidden());
        jdbc.update("UPDATE identity_user_account SET enabled = true WHERE id = UUID_TO_BIN(?)", leader.toString());
        inactivity.forward(leader, reminder.caseId(), cityUser, "地市跟进");
        jdbc.update("UPDATE identity_user_account SET city_id = NULL WHERE id = UUID_TO_BIN(?)", cityUser.toString());
        mvc.perform(post("/api/v1/projects/inactivity-cases/{id}/read", reminder.caseId())
                .header("X-NDI-Actor-Id", cityUser.toString())).andExpect(status().isForbidden());
        assertThat(inactivity.unreadCount(cityUser)).isZero();
    }

    @Test
    void escalatesForwardsAndClosesOneDurableChainWithoutDuplicateNotices() {
        var project = create();
        var lastAt = age(project, 40);
        assertThat(inactivity.recordIfDue(project, lastAt.plus(30, ChronoUnit.DAYS).minusSeconds(1))).isEmpty();
        var first = inactivity.recordIfDue(project, lastAt.plus(30, ChronoUnit.DAYS)).orElseThrow();
        assertThat(first.delivered()).isTrue();
        assertThat(first.recipients()).isEqualTo(1);
        assertThat(inactivity.recordIfDue(project, lastAt.plus(31, ChronoUnit.DAYS)).orElseThrow().delivered()).isFalse();
        assertThat(inactivity.findMine(leader, false, page).getContent()).hasSize(1);
        assertThat(inactivity.findMine(cityUser, false, page)).isEmpty();

        var second = inactivity.recordIfDue(project, lastAt.plus(33, ChronoUnit.DAYS)).orElseThrow();
        assertThat(second.caseId()).isEqualTo(first.caseId());
        assertThat(second.recipients()).isEqualTo(1);
        assertThat(inactivity.recipients(reviewer, first.caseId())).extracting(user -> user.id()).containsExactly(leader);
        inactivity.forward(reviewer, first.caseId(), leader, "请省分核实当前项目状态");
        assertThat(inactivity.findMine(secondReviewer, false, page)).isEmpty();
        assertThat(inactivity.findMine(secondReviewer, true, page)).isEmpty();
        inactivity.forward(leader, first.caseId(), cityUser, "请地市更新客户交流情况");
        assertThat(inactivity.findMine(cityUser, false, page).getContent()).singleElement()
                .satisfies(item -> assertThat(item.canForward()).isFalse());

        assertThat(inactivity.recordIfDue(project, lastAt.plus(36, ChronoUnit.DAYS)).orElseThrow().audience()).isEqualTo("GROUP");
        inactivity.forward(group, first.caseId(), leader, "集团要求跟进项目进度");
        inactivity.forward(leader, first.caseId(), cityUser, "同步集团跟进要求");
        assertThat(count("project_inactivity_notice", "case_id", first.caseId(), "AND kind = 'CITY'")).isEqualTo(1);
        var cityNotice = inactivity.findMine(cityUser, false, page).getContent().getFirst();
        assertThat(cityNotice.events()).hasSize(7);
        assertThat(cityNotice.events()).extracting(event -> event.summary()).anyMatch(text -> text.contains("同步集团"));

        long baseVersion = projectRepository.findById(project).orElseThrow().versionNo();
        progresses.append(cityUser, project, new AppendProgressCommand("已联系客户", "准备下一轮交流", null));
        assertThat(projectRepository.findById(project).orElseThrow().versionNo()).isEqualTo(baseVersion);
        for (var user : List.of(cityUser, leader, reviewer, group)) {
            assertThat(inactivity.findMine(user, false, page)).isEmpty();
            assertThat(inactivity.findMine(user, true, page).getContent()).singleElement()
                    .satisfies(item -> assertThat(item.status()).isEqualTo("RESOLVED"));
        }
        assertThat(inactivity.recordIfDue(project, Instant.now())).isEmpty();
        var closed = catchThrowable(() -> inactivity.forward(leader, first.caseId(), cityUser, "不能转发已闭环事项"));
        assertThat(((BusinessException) closed).errorCode()).isEqualTo(ProjectErrorCode.INACTIVITY_NOT_ACTIONABLE);
        var nextLastAt = age(project, 31);
        var next = inactivity.recordIfDue(project, nextLastAt.plus(30, ChronoUnit.DAYS)).orElseThrow();
        assertThat(next.caseId()).isNotEqualTo(first.caseId());
        assertThat(inactivity.findMine(leader, true, page).getContent()).hasSize(1);
    }

    @Test
    void pendingRejectedAndWithdrawnRequestsDoNotResolveButApprovedChangeDoes() {
        var project = create();
        var lastAt = age(project, 40);
        var reminder = inactivity.recordIfDue(project, lastAt.plus(30, ChronoUnit.DAYS)).orElseThrow();
        var request = approvals.submitMajorChange(cityUser, project, rename("名称调整一"));
        assertThat(mapper.find(reminder.caseId()).orElseThrow().status()).isEqualTo("OPEN");
        approvals.decide(leader, request.id(), false, "需核实客户名称");
        assertThat(mapper.find(reminder.caseId()).orElseThrow().status()).isEqualTo("OPEN");
        request = approvals.submitMajorChange(cityUser, project, rename("名称调整二"));
        approvals.withdraw(cityUser, request.id());
        assertThat(mapper.find(reminder.caseId()).orElseThrow().status()).isEqualTo("OPEN");
        request = approvals.submitMajorChange(cityUser, project, rename("名称调整三"));
        approvals.decide(leader, request.id(), true, null);
        assertThat(mapper.find(reminder.caseId()).orElseThrow().status()).isEqualTo("RESOLVED");
    }

    @Test
    void progressWhileApprovalIsPendingResetsActivityWithoutInvalidatingApproval() {
        var project = create();
        var request = approvals.submitMajorChange(cityUser, project, rename("继续审批的名称"));
        progresses.append(cityUser, project, new AppendProgressCommand("客户已反馈", "补充新方案", null));
        assertThat(approvals.decide(leader, request.id(), true, null).status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(projectRepository.findById(project).orElseThrow().projectName()).isEqualTo("继续审批的名称");
    }

    @Test
    void onlyChangedProfileClosesTheCaseAndNoOpKeepsItPending() {
        var project = create();
        var lastAt = age(project, 40);
        var reminder = inactivity.recordIfDue(project, lastAt.plus(30, ChronoUnit.DAYS)).orElseThrow();
        var detail = projects.findDetail(cityUser, project);
        profiles.update(cityUser, project, new UpdateProjectProfileCommand(detail.versionNo(), detail.customerName(),
                detail.industryCode(), detail.customerNeed(), leader, detail.cityAccountManagerName()));
        assertThat(mapper.find(reminder.caseId()).orElseThrow().status()).isEqualTo("OPEN");
        profiles.update(cityUser, project, new UpdateProjectProfileCommand(detail.versionNo(), "新客户名称",
                detail.industryCode(), detail.customerNeed(), leader, detail.cityAccountManagerName()));
        assertThat(mapper.find(reminder.caseId()).orElseThrow().status()).isEqualTo("RESOLVED");
    }

    @Test
    void paginatesCandidatesAndFirstScanAt36DaysStartsAtGroupTier() {
        var ids = List.of(create("督办项目甲"), create("督办项目乙"), create("督办项目丙"));
        var lastAt = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(40, ChronoUnit.DAYS);
        for (var id : ids) jdbc.update("UPDATE project_activity_state SET last_activity_at = ? WHERE project_id = UUID_TO_BIN(?)",
                LocalDateTime.ofInstant(lastAt, ZoneOffset.UTC), id.toString());
        var first = mapper.candidates(Instant.now().minus(30, ChronoUnit.DAYS), Instant.EPOCH, new UUID(0, 0), 2);
        assertThat(first).hasSize(2);
        var last = first.getLast();
        var second = mapper.candidates(Instant.now().minus(30, ChronoUnit.DAYS), last.lastActivityAt(), last.id(), 2);
        assertThat(second).hasSize(1);
        assertThat(java.util.stream.Stream.concat(first.stream(), second.stream()).map(ProjectInactivityMapper.Candidate::id))
                .containsExactlyInAnyOrderElementsOf(ids);
        new ProjectInactivityJob(mapper, inactivity, java.time.Clock.systemUTC()).scan();
        assertThat(inactivity.findMine(group, false, page).getContent()).hasSize(3);
        assertThat(inactivity.findMine(leader, false, page)).isEmpty();
        assertThat(inactivity.findMine(reviewer, false, page)).isEmpty();
    }

    @Test
    void retriesMissingRecipientsAndSkipsTerminatedProjects() {
        var project = create();
        var lastAt = age(project, 40);
        jdbc.update("UPDATE identity_user_account SET enabled = false WHERE id = UUID_TO_BIN(?)", leader.toString());
        var missing = inactivity.recordIfDue(project, lastAt.plus(30, ChronoUnit.DAYS)).orElseThrow();
        assertThat(missing.delivered()).isTrue();
        assertThat(missing.audience()).isEqualTo("INTERFACE");
        assertThat(mapper.find(missing.caseId()).orElseThrow().provinceSentAt()).isNotNull();
        jdbc.update("UPDATE identity_user_account SET enabled = true WHERE id = UUID_TO_BIN(?)", leader.toString());
        assertThat(inactivity.recordIfDue(project, lastAt.plus(30, ChronoUnit.DAYS)).orElseThrow().delivered()).isFalse();
        var request = approvals.submitMajorChange(cityUser, project, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, leader, "客户取消项目", null, null, null));
        approvals.decide(leader, request.id(), true, null);
        assertThat(mapper.find(missing.caseId()).orElseThrow().status()).isEqualTo("RESOLVED");
        assertThat(inactivity.recordIfDue(project, Instant.now().plus(60, ChronoUnit.DAYS))).isEmpty();
        var denied = catchThrowable(() -> inactivity.trigger(administrator, project, 30));
        assertThat(((BusinessException) denied).errorCode()).isEqualTo(ProjectErrorCode.TERMINATED_READ_ONLY);
    }

    @Test
    void validatesRecipientsScopeAndAdminOnlyManualTrigger() throws Exception {
        var project = create();
        var before = projectRepository.findById(project).orElseThrow();
        mvc.perform(post("/api/v1/projects/admin/inactivity-reminders/trigger")
                        .header("X-NDI-Actor-Id", cityUser.toString()).contentType("application/json")
                        .content("{\"projectId\":\"" + project + "\",\"days\":30}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/projects/admin/inactivity-reminders/trigger")
                        .header("X-NDI-Actor-Id", administrator.toString()).contentType("application/json")
                        .content("{\"projectId\":\"" + project + "\",\"days\":31}"))
                .andExpect(status().isBadRequest());
        var triggered = inactivity.trigger(administrator, project, 30);
        assertThat(projectRepository.findById(project).orElseThrow().updatedAt()).isEqualTo(before.updatedAt());
        mvc.perform(get("/api/v1/projects/inactivity-notices").header("X-NDI-Actor-Id", leader.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].canForward").value(true));
        mvc.perform(post("/api/v1/projects/inactivity-cases/{id}/forward", triggered.caseId())
                        .header("X-NDI-Actor-Id", leader.toString()).contentType("application/json")
                        .content("{\"recipientId\":\"" + reviewer + "\",\"note\":\"错误接收人\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/projects/inactivity-cases/{id}/forward", triggered.caseId())
                        .header("X-NDI-Actor-Id", leader.toString()).contentType("application/json")
                        .content("{\"recipientId\":\"" + cityUser + "\",\"note\":\" \"}"))
                .andExpect(status().isBadRequest());
        assertThat(count("project_inactivity_dispatch", "case_id", triggered.caseId(), "")).isZero();
        var otherCityId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '其他地市', true)",
                otherCityId.toString(), provinceId.toString(), otherCityId.toString().replace("-", ""));
        var outsider = addUser("CITY_REPORTER", provinceId, otherCityId);
        mvc.perform(get("/api/v1/projects/inactivity-cases/{id}/recipients", triggered.caseId())
                        .header("X-NDI-Actor-Id", outsider.toString())).andExpect(status().isForbidden());
        assertThat(inactivity.findMine(outsider, false, page)).isEmpty();
    }

    @Test
    void auditHistoryIsScopedPaginatedAndLimitedToReminderEvents() throws Exception {
        var project = create();
        var before = projectRepository.findById(project).orElseThrow();
        var triggered = inactivity.trigger(administrator, project, 30);
        inactivity.trigger(administrator, project, 30);
        inactivity.forward(leader, triggered.caseId(), cityUser, "请补充进展");
        progresses.append(cityUser, project, new AppendProgressCommand("已更新客户需求", "准备方案", null));
        mapper.audit(project, null, administrator, "UNRELATED_SECRET", "不应返回的其他审计", Instant.now());
        inactivity.trigger(administrator, create("其他项目"), 30);
        var history = inactivity.auditHistory(cityUser, project, page);
        assertThat(history.getTotalElements()).isEqualTo(5);
        assertThat(history).extracting(item -> item.action()).contains("INACTIVITY_REMINDER_PROVINCE",
                "INACTIVITY_MANUAL_TRIGGER", "INACTIVITY_FORWARD", "INACTIVITY_RESOLVED")
                .doesNotContain("UNRELATED_SECRET");
        assertThat(history).extracting(item -> item.actorName()).doesNotContainNull().contains("系统");
        var first = inactivity.auditHistory(administrator, project, PageRequest.of(0, 2));
        var second = inactivity.auditHistory(administrator, project, PageRequest.of(1, 2));
        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2);
        assertThat(first.getContent()).doesNotContainAnyElementsOf(second.getContent());
        assertThat(inactivity.auditHistory(administrator, project, PageRequest.of(9, 2))).isEmpty();
        mvc.perform(get("/api/v1/projects/{id}/inactivity-audit", project)
                        .header("X-NDI-Actor-Id", cityUser.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(5));
        mvc.perform(get("/api/v1/projects/{id}/inactivity-audit?size=101", project)
                        .header("X-NDI-Actor-Id", cityUser.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/{id}/inactivity-audit", project)).andExpect(status().isUnauthorized());
        var otherCity = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '其他地市', true)",
                otherCity.toString(), provinceId.toString(), otherCity.toString().replace("-", ""));
        var outsider = addUser("CITY_REPORTER", provinceId, otherCity);
        mvc.perform(get("/api/v1/projects/{id}/inactivity-audit", project)
                        .header("X-NDI-Actor-Id", outsider.toString())).andExpect(status().isForbidden());
        // 督办演练自身不改变项目版本；追加进展也沿用独立活动版本。
        assertThat(projectRepository.findById(project).orElseThrow().versionNo()).isEqualTo(before.versionNo());
    }

    @Test
    void serializesConcurrentScansAndForwards() throws Exception {
        var project = create();
        var lastAt = age(project, 40);
        var at = lastAt.plus(30, ChronoUnit.DAYS);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var one = pool.submit(() -> { start.await(); return inactivity.recordIfDue(project, at).orElseThrow(); });
            var two = pool.submit(() -> { start.await(); return inactivity.recordIfDue(project, at).orElseThrow(); });
            start.countDown();
            var first = one.get(15, TimeUnit.SECONDS);
            var second = two.get(15, TimeUnit.SECONDS);
            assertThat(first.caseId()).isEqualTo(second.caseId());
            assertThat(List.of(first.delivered(), second.delivered())).containsExactlyInAnyOrder(true, false);
            assertThat(count("project_inactivity_notice", "case_id", first.caseId(), "")).isEqualTo(1);
            var forwardStart = new CountDownLatch(1);
            var forwardOne = pool.submit(() -> { forwardStart.await(); return catchThrowable(
                    () -> inactivity.forward(leader, first.caseId(), cityUser, "并发转发一")); });
            var forwardTwo = pool.submit(() -> { forwardStart.await(); return catchThrowable(
                    () -> inactivity.forward(leader, first.caseId(), cityUser, "并发转发二")); });
            forwardStart.countDown();
            var results = java.util.Arrays.asList(forwardOne.get(15, TimeUnit.SECONDS), forwardTwo.get(15, TimeUnit.SECONDS));
            assertThat(results).filteredOn(java.util.Objects::isNull).hasSize(1);
            assertThat(results).filteredOn(java.util.Objects::nonNull).singleElement().isInstanceOf(BusinessException.class);
            assertThat(count("project_inactivity_dispatch", "case_id", first.caseId(), "")).isEqualTo(1);
        }
    }

    @Test
    void scansDifferentProjectsConcurrentlyWithoutSharingCaseGapLocks() throws Exception {
        var one = create("独立督办项目甲");
        var two = create("独立督办项目乙");
        var oneAt = age(one, 40).plus(30, ChronoUnit.DAYS);
        var twoAt = age(two, 40).plus(30, ChronoUnit.DAYS);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return inactivity.recordIfDue(one, oneAt).orElseThrow(); });
            var second = pool.submit(() -> { start.await(); return inactivity.recordIfDue(two, twoAt).orElseThrow(); });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).delivered()).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS).delivered()).isTrue();
            assertThat(inactivity.findMine(leader, false, page).getContent()).hasSize(2);
        }
    }

    private UUID create() {
        return create("超时督办项目");
    }

    private UUID create(String name) {
        return projects.createProject(cityUser, new CreateProjectCommand(provinceId, cityId, name, "客户",
                "C25", "客户需求", new BigDecimal("100.00"), leader, "地市客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "已开展初次交流", "继续跟进", null, true, "集成测试：独立督办项目")).id();
    }

    private Instant age(UUID projectId, int days) {
        var at = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(days, ChronoUnit.DAYS);
        jdbc.update("UPDATE project_activity_state SET last_activity_at = ? WHERE project_id = UUID_TO_BIN(?)",
                LocalDateTime.ofInstant(at, ZoneOffset.UTC), projectId.toString());
        return at;
    }

    private MajorChangeCommand rename(String name) {
        return new MajorChangeCommand(MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, leader,
                "客户调整名称", name, null, null);
    }

    private UUID addUser(String role, UUID province, UUID city) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled)
                VALUES (UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), true)
                """, id.toString(), id.toString(), role + "-" + id.toString().substring(0, 5),
                province == null ? null : province.toString(), city == null ? null : city.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), ?)", id.toString(), role);
        if (role.equals("INTERFACE_REVIEWER") && province != null) jdbc.update("INSERT INTO identity_reviewer_province (reviewer_id, province_id) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?))", id.toString(), province.toString());
        return id;
    }

    private long count(String table, String column, UUID id, String condition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = UUID_TO_BIN(?) " + condition,
                Long.class, id.toString());
    }
}
