package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.documents.service.DocumentSummary;
import com.pluto.ndi.documents.service.DownloadIntent;
import com.pluto.ndi.documents.service.UploadCommand;
import com.pluto.ndi.documents.service.UploadIntent;
import com.pluto.ndi.documents.domain.DocumentStatus;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ApprovalNotificationKind;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectDocumentPurpose;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.service.CreateProjectCommand;
import com.pluto.ndi.projects.service.MajorChangeCommand;
import com.pluto.ndi.projects.service.ProjectApprovalService;
import com.pluto.ndi.projects.service.ProjectApprovalInboxService;
import com.pluto.ndi.projects.service.ProjectApprovalNotificationService;
import com.pluto.ndi.projects.service.ProjectApprovalReminderWriter;
import com.pluto.ndi.projects.service.ProjectApprovalReminderJob;
import com.pluto.ndi.projects.service.ProjectProgressService;
import com.pluto.ndi.projects.service.ProjectDocumentService;
import com.pluto.ndi.projects.service.ProjectProfileService;
import com.pluto.ndi.projects.service.ProjectWorkbenchService;
import com.pluto.ndi.projects.service.AppendProgressCommand;
import com.pluto.ndi.projects.service.UpdateProjectProfileCommand;
import com.pluto.ndi.projects.service.ProjectService;
import com.pluto.ndi.projects.service.ProjectStageService;
import com.pluto.ndi.projects.service.StageAdvanceCommand;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest(classes = NdiApplication.class)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProjectApprovalIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi")
            .withUsername("ndi")
            .withPassword("ndi_test_password");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectService projectService;
    @Autowired private ProjectApprovalService approvalService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectApprovalRequestRepository approvalRepository;
    @Autowired private ProjectStageService stageService;
    @Autowired private ProjectApprovalInboxService inboxService;
    @Autowired private ProjectApprovalNotificationService notificationService;
    @Autowired private ProjectApprovalReminderWriter reminderWriter;
    @Autowired private ProjectApprovalReminderJob reminderJob;
    @Autowired private ProjectProgressService progressService;
    @Autowired private ProjectDocumentService projectDocumentService;
    @Autowired private ProjectProfileService profileService;
    @Autowired private ProjectWorkbenchService workbenchService;
    @MockitoBean private DocumentDirectory documentDirectory;

    private UUID provinceId;
    private UUID cityId;
    private UUID cityUserId;
    private UUID provinceUserId;
    private UUID interfaceUserId;

    @BeforeEach
    void seedIdentity() {
        provinceId = UUID.randomUUID();
        cityId = UUID.randomUUID();
        cityUserId = UUID.randomUUID();
        provinceUserId = UUID.randomUUID();
        interfaceUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_province (id, province_code, province_name, enabled) VALUES (UUID_TO_BIN(?), ?, ?, true)",
                provinceId.toString(), provinceId.toString().replace("-", ""), "测试省分");
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, true)",
                cityId.toString(), provinceId.toString(), cityId.toString().replace("-", ""), "测试地市");
        addUser(cityUserId, "CITY_REPORTER", true);
        addUser(provinceUserId, "PROVINCE_REPORTER", false);
        addUser(interfaceUserId, "INTERFACE_REVIEWER", false);
    }

    @Test
    void approvedNameChangeOnlyUpdatesFormalProjectAfterDecision() {
        var projectId = createProject();
        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户调整了正式项目名称", "更新后的项目", null, null));

        assertThat(request.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(Duration.between(request.submittedAt(), request.dueAt())).isEqualTo(Duration.ofHours(48));
        assertThat(projectRepository.findById(projectId).orElseThrow().projectName()).isEqualTo("原项目");

        var approved = approvalService.decide(provinceUserId, request.id(), true, "同意");
        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(projectRepository.findById(projectId).orElseThrow().projectName()).isEqualTo("更新后的项目");
        assertThat(approvalRepository.existsByProjectIdAndStatus(projectId, ApprovalStatus.PENDING)).isFalse();
    }

    @Test
    void rollbackClearsContractButTerminationPreservesIt() {
        var rollbackProjectId = createProject();
        setSignedProject(rollbackProjectId, "CONTRACT-ROLLBACK-" + UUID.randomUUID());
        var rollback = approvalService.submitMajorChange(cityUserId, rollbackProjectId, new MajorChangeCommand(
                MajorChangeType.STAGE_ROLLBACK, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户重新组织招投标", null, null, ProjectStage.BIDDING));
        approvalService.decide(provinceUserId, rollback.id(), true, null);
        var rolledBack = projectRepository.findById(rollbackProjectId).orElseThrow();
        assertThat(rolledBack.currentStageCode()).isEqualTo(ProjectStage.BIDDING.name());
        assertThat(rolledBack.contractNo()).isNull();
        assertThat(rolledBack.signedAmount()).isNull();

        var terminatedProjectId = createProject();
        var contractNo = "CONTRACT-TERMINATE-" + UUID.randomUUID();
        setSignedProject(terminatedProjectId, contractNo);
        var terminate = approvalService.submitMajorChange(provinceUserId, terminatedProjectId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.PROVINCE, interfaceUserId,
                "客户取消项目", null, null, null));
        approvalService.decide(interfaceUserId, terminate.id(), true, null);
        var terminated = projectRepository.findById(terminatedProjectId).orElseThrow();
        assertThat(terminated.currentStageCode()).isEqualTo(ProjectStage.TERMINATED.name());
        assertThat(terminated.contractNo()).isEqualTo(contractNo);
        assertThat(terminated.signedAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void enforcesSinglePendingAndUniqueApprover() {
        var projectId = createProject();
        var invalid = catchThrowable(() -> approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, interfaceUserId,
                "申请名称变更", "新名称", null, null)));
        assertThat(invalid).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) invalid).errorCode()).isEqualTo(ProjectErrorCode.APPROVER_INVALID);

        var adminId = UUID.randomUUID();
        addUser(adminId, "PROVINCE_REPORTER", false);
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), 'SYSTEM_ADMIN')",
                adminId.toString());
        var adminApprover = catchThrowable(() -> approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, adminId,
                "申请名称变更", "新名称", null, null)));
        assertThat(adminApprover).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) adminApprover).errorCode()).isEqualTo(ProjectErrorCode.APPROVER_INVALID);

        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "申请名称变更", "新名称", null, null));
        var duplicate = catchThrowable(() -> approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, provinceUserId,
                "项目停止", null, null, null)));
        assertThat(duplicate).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) duplicate).errorCode()).isEqualTo(ProjectErrorCode.PENDING_APPROVAL_EXISTS);

        var unauthorized = catchThrowable(() -> approvalService.decide(interfaceUserId, request.id(), true, null));
        assertThat(unauthorized).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) unauthorized).errorCode()).isEqualTo(ProjectErrorCode.APPROVAL_FORBIDDEN);
        assertThat(approvalService.withdraw(cityUserId, request.id()).status()).isEqualTo(ApprovalStatus.WITHDRAWN);
        assertThat(projectRepository.findById(projectId).orElseThrow().projectName()).isEqualTo("原项目");
    }

    @Test
    void rejectsInvalidAmountAndRequiresRejectionOpinion() {
        var projectId = createProject();
        var amountError = catchThrowable(() -> approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, provinceUserId,
                "项目预算缩减", null, new BigDecimal("50.00"), null)));
        assertThat(amountError).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) amountError).errorCode()).isEqualTo(ProjectErrorCode.INVALID_CATEGORY_AMOUNT);

        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, provinceUserId,
                "项目预算增加", null, new BigDecimal("120.00"), null));
        var noOpinion = catchThrowable(() -> approvalService.decide(provinceUserId, request.id(), false, " "));
        assertThat(noOpinion).isInstanceOf(BusinessException.class);
        assertThat(approvalService.decide(provinceUserId, request.id(), false, "缺少预算依据").status())
                .isEqualTo(ApprovalStatus.REJECTED);
        assertThat(projectRepository.findById(projectId).orElseThrow().totalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void marksStaleRequestConflictedWithoutApplyingProposal() {
        var projectId = createProject();
        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "名称调整", "建议名称", null, null));
        jdbc.update("UPDATE project_project SET version_no = version_no + 1 WHERE id = UUID_TO_BIN(?)", projectId.toString());

        var result = approvalService.decide(provinceUserId, request.id(), true, null);
        assertThat(result.status()).isEqualTo(ApprovalStatus.CONFLICT);
        assertThat(projectRepository.findById(projectId).orElseThrow().projectName()).isEqualTo("原项目");
    }

    @Test
    void advancesAllThreeStagesOnlyAfterApprovalAndValidatedEvidence() {
        var projectId = createProject();
        var solutionId = createAvailableDocument(projectId, "CUSTOMER_SOLUTION");
        var awardId = createAvailableDocument(projectId, "AWARD_NOTICE");
        when(documentDirectory.isUsable("PROJECT", projectId, solutionId, "CUSTOMER_SOLUTION")).thenReturn(true);
        when(documentDirectory.isUsable("PROJECT", projectId, awardId, "AWARD_NOTICE")).thenReturn(true);
        var occurredOn = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        var missingEvidence = catchThrowable(() -> stageService.submit(cityUserId, projectId, new StageAdvanceCommand(
                ProjectInitiatorLevel.CITY, ProjectStage.BIDDING, interfaceUserId, occurredOn,
                null, null, null, null, null)));
        assertThat(missingEvidence).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) missingEvidence).errorCode()).isEqualTo(ProjectErrorCode.STAGE_EVIDENCE_REQUIRED);

        var bidding = stageService.submit(cityUserId, projectId, new StageAdvanceCommand(
                ProjectInitiatorLevel.CITY, ProjectStage.BIDDING, interfaceUserId, occurredOn,
                solutionId, null, null, "客户方案已确认", "安排招投标"));
        assertThat(projectRepository.findById(projectId).orElseThrow().currentStageCode())
                .isEqualTo(ProjectStage.SOLUTION_EXCHANGE.name());
        var wrongApprover = catchThrowable(() -> approvalService.decide(provinceUserId, bidding.id(), true, null));
        assertThat(wrongApprover).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) wrongApprover).errorCode()).isEqualTo(ProjectErrorCode.APPROVAL_FORBIDDEN);
        assertThat(approvalService.decide(interfaceUserId, bidding.id(), true, null).status())
                .isEqualTo(ApprovalStatus.APPROVED);

        var signed = stageService.submit(provinceUserId, projectId, new StageAdvanceCommand(
                ProjectInitiatorLevel.PROVINCE, ProjectStage.SIGNED, interfaceUserId, occurredOn,
                awardId, null, null, null, null));
        approvalService.decide(interfaceUserId, signed.id(), true, null);

        var collection = stageService.submit(cityUserId, projectId, new StageAdvanceCommand(
                ProjectInitiatorLevel.CITY, ProjectStage.COLLECTION, interfaceUserId, occurredOn,
                null, "NDI-CONTRACT-" + UUID.randomUUID(), new BigDecimal("88.00"), null, null));
        approvalService.decide(interfaceUserId, collection.id(), true, null);
        var project = projectRepository.findById(projectId).orElseThrow();
        assertThat(project.currentStageCode()).isEqualTo(ProjectStage.COLLECTION.name());
        assertThat(project.contractNo()).startsWith("NDI-CONTRACT-");
        assertThat(project.signedAmount()).isEqualByComparingTo("88.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_stage_milestone WHERE project_id = UUID_TO_BIN(?)",
                Integer.class, projectId.toString())).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_progress WHERE project_id = UUID_TO_BIN(?)",
                Integer.class, projectId.toString())).isEqualTo(2);
    }

    @Test
    void rejectsWrongStageEvidenceAndUnsignedCollection() {
        var projectId = createProject();
        var awardId = createAvailableDocument(projectId, "AWARD_NOTICE");
        var occurredOn = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var wrongEvidence = catchThrowable(() -> stageService.submit(cityUserId, projectId, new StageAdvanceCommand(
                ProjectInitiatorLevel.CITY, ProjectStage.BIDDING, interfaceUserId, occurredOn,
                awardId, null, null, null, null)));
        assertThat(wrongEvidence).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) wrongEvidence).errorCode()).isEqualTo(ProjectErrorCode.STAGE_EVIDENCE_REQUIRED);

        var skipped = catchThrowable(() -> stageService.submit(cityUserId, projectId, new StageAdvanceCommand(
                ProjectInitiatorLevel.CITY, ProjectStage.COLLECTION, interfaceUserId, occurredOn,
                null, "CONTRACT", new BigDecimal("50.00"), null, null)));
        assertThat(skipped).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) skipped).errorCode()).isEqualTo(ProjectErrorCode.INVALID_STAGE_ADVANCEMENT);
    }

    @Test
    void marksContractCollisionAsConflictBeforeApproval() {
        var projectId = createProject();
        var otherProjectId = createProject();
        forceSigned(projectId);
        forceSigned(otherProjectId);
        var contractNo = "SHARED-" + UUID.randomUUID();
        var request = stageService.submit(cityUserId, projectId, new StageAdvanceCommand(
                ProjectInitiatorLevel.CITY, ProjectStage.COLLECTION, interfaceUserId,
                LocalDate.now(ZoneId.of("Asia/Shanghai")), null, contractNo,
                new BigDecimal("90.00"), null, null));
        jdbc.update("UPDATE project_project SET contract_no = ? WHERE id = UUID_TO_BIN(?)",
                contractNo, otherProjectId.toString());

        assertThat(approvalService.decide(interfaceUserId, request.id(), true, null).status())
                .isEqualTo(ApprovalStatus.CONFLICT);
        assertThat(projectRepository.findById(projectId).orElseThrow().currentStageCode())
                .isEqualTo(ProjectStage.SIGNED.name());
    }

    @Test
    void inboxAndNotificationsStayScopedToTheAssignedUsers() throws Exception {
        var projectId = createProject();
        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户调整名称", "新的项目名称", null, null));

        var tasks = inboxService.findPending(provinceUserId, PageRequest.of(0, 20));
        assertThat(tasks.getContent()).extracting(task -> task.requestId()).contains(request.id());
        mockMvc.perform(get("/api/v1/projects/approvals/inbox")
                        .header("X-NDI-Actor-Id", provinceUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].requestId").value(request.id().toString()));
        assertThat(inboxService.findPending(interfaceUserId, PageRequest.of(0, 20)).getContent()).isEmpty();
        var notice = notificationService.findMine(provinceUserId, PageRequest.of(0, 20)).getContent().getFirst();
        assertThat(notice.requestId()).isEqualTo(request.id());
        assertThat(notice.kind()).isEqualTo(ApprovalNotificationKind.SUBMITTED);
        assertThat(notice.readAt()).isNull();
        var wrongRecipient = catchThrowable(() -> notificationService.markRead(interfaceUserId, notice.id()));
        assertThat(wrongRecipient).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) wrongRecipient).errorCode()).isEqualTo(ProjectErrorCode.NOTIFICATION_NOT_FOUND);
        assertThat(notificationService.markRead(provinceUserId, notice.id()).readAt()).isNotNull();

        approvalService.decide(provinceUserId, request.id(), false, "名称尚未确认");
        assertThat(inboxService.findPending(provinceUserId, PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(notificationService.findMine(cityUserId, PageRequest.of(0, 20)).getContent())
                .extracting(notification -> notification.kind()).contains(ApprovalNotificationKind.REJECTED);
    }

    @Test
    void overdueReminderIsIdempotentAndStopsAfterWithdrawal() {
        var projectId = createProject();
        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "名称变更", "新的项目名称", null, null));

        assertThat(reminderWriter.recordIfDue(request.id(), request.dueAt().minusSeconds(1))).isFalse();
        reminderWriter.recordIfDue(request.id(), request.dueAt().plusSeconds(1));
        reminderWriter.recordIfDue(request.id(), request.dueAt().plusSeconds(2));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_approval_notification
                WHERE request_id = UUID_TO_BIN(?) AND kind = 'OVERDUE'
                """, Integer.class, request.id().toString())).isEqualTo(1);

        reminderWriter.recordIfDue(request.id(), request.dueAt().plusSeconds(24 * 60 * 60 + 1));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_approval_notification
                WHERE request_id = UUID_TO_BIN(?) AND kind = 'OVERDUE'
                """, Integer.class, request.id().toString())).isEqualTo(2);
        approvalService.withdraw(cityUserId, request.id());
        assertThat(reminderWriter.recordIfDue(request.id(), request.dueAt().plusSeconds(48 * 60 * 60))).isFalse();
        assertThat(notificationService.findMine(provinceUserId, PageRequest.of(0, 20)).getContent())
                .extracting(notification -> notification.kind())
                .contains(ApprovalNotificationKind.WITHDRAWN, ApprovalNotificationKind.OVERDUE);
    }

    @Test
    void scheduledScanCreatesOneReminderForAnOverduePendingRequest() {
        var projectId = createProject();
        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "名称变更", "新的项目名称", null, null));
        var secondProjectId = createProject();
        var secondRequest = approvalService.submitMajorChange(cityUserId, secondProjectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "名称变更", "另一个项目名称", null, null));
        jdbc.update("""
                UPDATE project_approval_request SET due_at = NOW(6) - INTERVAL 1 SECOND
                WHERE id IN (UUID_TO_BIN(?), UUID_TO_BIN(?))
                """, request.id().toString(), secondRequest.id().toString());

        var first = approvalRepository.findOverdueAfter(
                ApprovalStatus.PENDING, Instant.now().plusSeconds(5), Instant.EPOCH,
                new UUID(0, 0), PageRequest.of(0, 1)).getFirst();
        var second = approvalRepository.findOverdueAfter(
                ApprovalStatus.PENDING, Instant.now().plusSeconds(5), first.dueAt(),
                first.id(), PageRequest.of(0, 1)).getFirst();
        assertThat(List.of(first.id(), second.id())).containsExactlyInAnyOrder(request.id(), secondRequest.id());

        reminderJob.scan();
        reminderJob.scan();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_approval_notification
                WHERE request_id = UUID_TO_BIN(?) AND kind = 'OVERDUE'
                """, Integer.class, request.id().toString())).isEqualTo(1);
        assertThat(inboxService.findPending(provinceUserId, PageRequest.of(0, 20)).getContent())
                .anySatisfy(task -> {
                    assertThat(task.requestId()).isEqualTo(request.id());
                    assertThat(task.overdue()).isTrue();
                });
    }

    @Test
    void appendsAndCorrectsProgressWithoutInvalidatingPendingApproval() throws Exception {
        var projectId = createProject();
        var original = progressService.findProgress(cityUserId, projectId, PageRequest.of(0, 20))
                .getContent().getFirst();
        var version = projectRepository.findById(projectId).orElseThrow().versionNo();
        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户正式名称变化", "更新后的名称", null, null));

        var added = progressService.append(interfaceUserId, projectId,
                new AppendProgressCommand("  接口人补充进展  ", "下一步联络客户", null));
        var correction = progressService.append(provinceUserId, projectId,
                new AppendProgressCommand("原始交流记录更正", "重新确认需求", original.id()));

        assertThat(added.progressContent()).isEqualTo("接口人补充进展");
        assertThat(correction.correctsProgressId()).isEqualTo(original.id());
        assertThat(progressService.findProgress(cityUserId, projectId, PageRequest.of(0, 20)).getContent())
                .extracting(progress -> progress.id()).contains(original.id(), added.id(), correction.id());
        assertThat(projectRepository.findById(projectId).orElseThrow().versionNo()).isEqualTo(version);
        assertThat(approvalService.decide(provinceUserId, request.id(), true, null).status())
                .isEqualTo(ApprovalStatus.APPROVED);
        assertThat(projectService.findDetail(interfaceUserId, projectId).projectName()).isEqualTo("更新后的名称");
        assertThat(projectService.findDetail(cityUserId, projectId).customerNeed()).isEqualTo("客户需要系统升级");
        assertThat(progressService.findMilestones(interfaceUserId, projectId, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(1);

        mockMvc.perform(get("/api/v1/projects/{projectId}", projectId)
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectName").value("更新后的名称"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/progress", projectId)
                        .header("X-NDI-Actor-Id", interfaceUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(post("/api/v1/projects/{projectId}/progress", projectId)
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json")
                        .content("{\"progressContent\":\"接口新增进展\",\"nextAction\":\"继续跟进\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.progressContent").value("接口新增进展"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/progress", projectId)
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json")
                        .content("{\"progressContent\":\"   \",\"nextAction\":\"继续跟进\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesOnlyNormalProfileFieldsAndConflictsWithEarlierPendingApproval() throws Exception {
        var projectId = createProject();
        var original = projectService.findDetail(cityUserId, projectId);
        var pending = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户调整项目名称", "审批中的新名称", null, null));
        var command = new UpdateProjectProfileCommand(original.versionNo(), "  新客户  ", "C25",
                "  客户增加上线要求  ", provinceUserId, "  新客户经理  ");

        var updated = profileService.update(cityUserId, projectId, command);
        assertThat(updated.versionNo()).isEqualTo(original.versionNo() + 1);
        assertThat(updated.customerName()).isEqualTo("新客户");
        assertThat(updated.customerNeed()).isEqualTo("客户增加上线要求");
        assertThat(updated.cityAccountManagerName()).isEqualTo("新客户经理");
        assertThat(updated.projectName()).isEqualTo(original.projectName());
        assertThat(updated.totalAmount()).isEqualByComparingTo(original.totalAmount());
        assertThat(updated.currentStageCode()).isEqualTo(original.currentStageCode());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_change_event
                WHERE project_id = UUID_TO_BIN(?) AND event_type = 'PROJECT_PROFILE_UPDATED'
                """, Integer.class, projectId.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload, '$.before.customerName'))
                FROM project_change_event
                WHERE project_id = UUID_TO_BIN(?) AND event_type = 'PROJECT_PROFILE_UPDATED'
                """, String.class, projectId.toString())).isEqualTo(original.customerName());

        var noChange = profileService.update(provinceUserId, projectId,
                new UpdateProjectProfileCommand(updated.versionNo(), "新客户", "C25",
                        "客户增加上线要求", provinceUserId, "新客户经理"));
        assertThat(noChange.versionNo()).isEqualTo(updated.versionNo());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_change_event
                WHERE project_id = UUID_TO_BIN(?) AND event_type = 'PROJECT_PROFILE_UPDATED'
                """, Integer.class, projectId.toString())).isEqualTo(1);
        assertThat(approvalService.decide(provinceUserId, pending.id(), true, null).status())
                .isEqualTo(ApprovalStatus.CONFLICT);

        mockMvc.perform(put("/api/v1/projects/{projectId}/profile", projectId)
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json")
                        .content("""
                                {"expectedVersion":%d,"customerName":"新客户","industryCode":"C25",
                                 "customerNeed":"客户增加上线要求","provinceLeaderUserId":"%s",
                                 "cityAccountManagerName":"新客户经理"}
                                """.formatted(updated.versionNo(), provinceUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(updated.versionNo()));
    }

    @Test
    void protectsNormalProfileUpdateWithRoleVersionReferenceAndTerminalChecks() throws Exception {
        var projectId = createProject();
        var version = projectRepository.findById(projectId).orElseThrow().versionNo();
        var valid = new UpdateProjectProfileCommand(version, "客户", "C25", "新需求", provinceUserId, "客户经理");

        var interfaceAttempt = catchThrowable(() -> profileService.update(interfaceUserId, projectId, valid));
        assertThat(((BusinessException) interfaceAttempt).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
        var adminId = UUID.randomUUID();
        addUser(adminId, "PROVINCE_REPORTER", false);
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), 'SYSTEM_ADMIN')",
                adminId.toString());
        var adminAttempt = catchThrowable(() -> profileService.update(adminId, projectId, valid));
        assertThat(((BusinessException) adminAttempt).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
        var otherCityId = UUID.randomUUID();
        var otherCityUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '其他地市', true)",
                otherCityId.toString(), provinceId.toString(), otherCityId.toString().replace("-", ""));
        jdbc.update("INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled) VALUES (UUID_TO_BIN(?), ?, '其他地市人员', UUID_TO_BIN(?), UUID_TO_BIN(?), true)",
                otherCityUserId.toString(), otherCityUserId.toString(), provinceId.toString(), otherCityId.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), 'CITY_REPORTER')",
                otherCityUserId.toString());
        var otherCityAttempt = catchThrowable(() -> profileService.update(otherCityUserId, projectId, valid));
        assertThat(((BusinessException) otherCityAttempt).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);

        var stale = catchThrowable(() -> profileService.update(cityUserId, projectId,
                new UpdateProjectProfileCommand(version + 1, "客户", "C25", "新需求", provinceUserId, "客户经理")));
        assertThat(((BusinessException) stale).errorCode()).isEqualTo(ProjectErrorCode.VERSION_CONFLICT);
        var invalidIndustry = catchThrowable(() -> profileService.update(cityUserId, projectId,
                new UpdateProjectProfileCommand(version, "客户", "XXX", "新需求", provinceUserId, "客户经理")));
        assertThat(((BusinessException) invalidIndustry).errorCode()).isEqualTo(ProjectErrorCode.INVALID_INDUSTRY);
        var invalidLeader = catchThrowable(() -> profileService.update(cityUserId, projectId,
                new UpdateProjectProfileCommand(version, "客户", "C25", "新需求", interfaceUserId, "客户经理")));
        assertThat(((BusinessException) invalidLeader).errorCode()).isEqualTo(ProjectErrorCode.INVALID_PROVINCE_LEADER);

        var termination = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户停止项目", null, null, null));
        approvalService.decide(provinceUserId, termination.id(), true, null);
        var terminal = catchThrowable(() -> profileService.update(cityUserId, projectId,
                new UpdateProjectProfileCommand(version + 1, "客户", "C25", "新需求", provinceUserId, "客户经理")));
        assertThat(((BusinessException) terminal).errorCode()).isEqualTo(ProjectErrorCode.TERMINATED_READ_ONLY);

        mockMvc.perform(put("/api/v1/projects/{projectId}/profile", projectId)
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json")
                        .content("{\"expectedVersion\":1,\"customerName\":\"客户\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workbenchUsesCurrentApprovedValuesAndExplicitTerminationFilter() throws Exception {
        var activeId = createProject();
        var terminatedId = createProject();
        var amountChange = approvalService.submitMajorChange(cityUserId, activeId, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户增加预算", null, new BigDecimal("150.00"), null));
        var termination = approvalService.submitMajorChange(cityUserId, terminatedId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户终止项目", null, null, null));

        var beforeDecisions = workbenchService.overview(cityUserId, false);
        assertThat(beforeDecisions.projectCount()).isEqualTo(2);
        assertThat(beforeDecisions.totalExpectedAmount()).isEqualByComparingTo("200.00");
        assertThat(beforeDecisions.stages()).hasSize(4);
        approvalService.decide(provinceUserId, amountChange.id(), true, null);
        approvalService.decide(provinceUserId, termination.id(), true, null);

        var defaultOverview = workbenchService.overview(cityUserId, false);
        assertThat(defaultOverview.projectCount()).isEqualTo(1);
        assertThat(defaultOverview.totalExpectedAmount()).isEqualByComparingTo("150.00");
        assertThat(defaultOverview.stages()).hasSize(4);
        assertThat(defaultOverview.stages().getFirst().projectCount()).isEqualTo(1);
        var includingTerminated = workbenchService.overview(interfaceUserId, true);
        assertThat(includingTerminated.projectCount()).isEqualTo(2);
        assertThat(includingTerminated.totalExpectedAmount()).isEqualByComparingTo("250.00");
        assertThat(includingTerminated.stages()).hasSize(5);
        assertThat(includingTerminated.stages().get(4).stageCode()).isEqualTo("TERMINATED");
        assertThat(includingTerminated.stages().get(4).projectCount()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/projects/workbench/overview")
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectCount").value(1))
                .andExpect(jsonPath("$.data.stages.length()").value(4));
        mockMvc.perform(get("/api/v1/projects/workbench/overview?includeTerminated=true")
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectCount").value(2))
                .andExpect(jsonPath("$.data.stages[4].projectCount").value(1));
    }

    @Test
    void workbenchRespectsCityScopeAndReturnsZeroForEmptyScope() throws Exception {
        createProject();
        var otherCityId = UUID.randomUUID();
        var otherCityUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '其他地市', true)",
                otherCityId.toString(), provinceId.toString(), otherCityId.toString().replace("-", ""));
        jdbc.update("INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled) VALUES (UUID_TO_BIN(?), ?, '其他地市人员', UUID_TO_BIN(?), UUID_TO_BIN(?), true)",
                otherCityUserId.toString(), otherCityUserId.toString(), provinceId.toString(), otherCityId.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), 'CITY_REPORTER')",
                otherCityUserId.toString());

        var empty = workbenchService.overview(otherCityUserId, false);
        assertThat(empty.projectCount()).isZero();
        assertThat(empty.totalExpectedAmount()).isEqualByComparingTo("0");
        assertThat(empty.stages()).allSatisfy(stage -> assertThat(stage.projectCount()).isZero());
        projectService.createProject(otherCityUserId, new CreateProjectCommand(
                provinceId, otherCityId, "其他地市项目", "客户", "C25", "独立需求",
                new BigDecimal("70.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("30.00"))),
                "完成初次沟通", "继续跟进"));
        assertThat(workbenchService.overview(cityUserId, false).projectCount()).isEqualTo(1);
        assertThat(workbenchService.overview(otherCityUserId, false).projectCount()).isEqualTo(1);
        assertThat(workbenchService.overview(provinceUserId, false).projectCount()).isEqualTo(2);
        assertThat(workbenchService.overview(interfaceUserId, false).totalExpectedAmount())
                .isEqualByComparingTo("170.00");

        var adminId = UUID.randomUUID();
        addUser(adminId, "SYSTEM_ADMIN", false);
        var adminRead = catchThrowable(() -> workbenchService.overview(adminId, false));
        assertThat(adminRead).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) adminRead).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
    }

    @Test
    void terminatedProjectRejectsNewAndPendingUploadsButRetainsAvailableDocuments() throws Exception {
        var projectId = createProject();
        var documentId = UUID.randomUUID();
        var command = new UploadCommand("GENERAL", "资料.pdf", "application/pdf", 3, "0".repeat(64));
        when(documentDirectory.beginUpload(eq("PROJECT"), eq(projectId), eq(cityUserId), any(UploadCommand.class)))
                .thenReturn(new UploadIntent(documentId, "PUT", "https://storage.example/upload", Map.of(),
                        Instant.now().plusSeconds(600)));
        assertThat(projectDocumentService.beginUpload(cityUserId, projectId, ProjectDocumentPurpose.GENERAL,
                command).documentId()).isEqualTo(documentId);

        var termination = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户终止项目", null, null, null));
        approvalService.decide(provinceUserId, termination.id(), true, null);
        var newUpload = catchThrowable(() -> projectDocumentService.beginUpload(cityUserId, projectId,
                ProjectDocumentPurpose.GENERAL, command));
        assertThat(((BusinessException) newUpload).errorCode()).isEqualTo(ProjectErrorCode.TERMINATED_READ_ONLY);
        var finishPending = catchThrowable(() -> projectDocumentService.completeUpload(cityUserId, projectId, documentId));
        assertThat(((BusinessException) finishPending).errorCode()).isEqualTo(ProjectErrorCode.TERMINATED_READ_ONLY);
        verify(documentDirectory, never()).completeUpload("PROJECT", projectId, cityUserId, documentId);

        var available = new DocumentSummary(UUID.randomUUID(), projectId, "GENERAL", "历史资料.pdf",
                "application/pdf", 3, "0".repeat(64), DocumentStatus.AVAILABLE, Instant.now());
        when(documentDirectory.listAvailable("PROJECT", projectId)).thenReturn(List.of(available));
        when(documentDirectory.download("PROJECT", projectId, available.id()))
                .thenReturn(new DownloadIntent("https://storage.example/download", Instant.now().plusSeconds(300)));
        assertThat(projectDocumentService.listAvailable(interfaceUserId, projectId)).containsExactly(available);
        assertThat(projectDocumentService.download(interfaceUserId, projectId, available.id()).downloadUrl())
                .isEqualTo("https://storage.example/download");

        mockMvc.perform(get("/api/v1/projects/{projectId}/documents", projectId)
                        .header("X-NDI-Actor-Id", interfaceUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(available.id().toString()));
        mockMvc.perform(get("/api/v1/projects/{projectId}/documents/{documentId}/download-url",
                        projectId, available.id())
                        .header("X-NDI-Actor-Id", interfaceUserId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/{projectId}/documents/uploads", projectId)
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json")
                        .content("""
                                {"purpose":"GENERAL","originalName":"资料.pdf","mediaType":"application/pdf",
                                 "byteSize":3,"sha256Hex":"%s"}
                                """.formatted("0".repeat(64))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_TERMINATED_READ_ONLY"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/documents/{documentId}/complete", projectId, documentId)
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isConflict());
        verify(documentDirectory).beginUpload(eq("PROJECT"), eq(projectId), eq(cityUserId), any(UploadCommand.class));
    }

    @Test
    void uploadCompletionAndTerminationAreSerializedByProjectLock() throws Exception {
        var projectId = createProject();
        var documentId = UUID.randomUUID();
        var termination = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户终止项目", null, null, null));
        var completionEntered = new CountDownLatch(1);
        var releaseCompletion = new CountDownLatch(1);
        var decisionStarted = new CountDownLatch(1);
        when(documentDirectory.completeUpload("PROJECT", projectId, cityUserId, documentId)).thenAnswer(invocation -> {
            completionEntered.countDown();
            if (!releaseCompletion.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待完成上传超时");
            }
            return new DocumentSummary(documentId, projectId, "GENERAL", "资料.pdf", "application/pdf",
                    3, "0".repeat(64), DocumentStatus.AVAILABLE, Instant.now());
        });

        try (var executor = Executors.newFixedThreadPool(2)) {
            var completion = executor.submit(() -> projectDocumentService.completeUpload(cityUserId, projectId, documentId));
            assertThat(completionEntered.await(10, TimeUnit.SECONDS)).isTrue();
            var decision = executor.submit(() -> {
                decisionStarted.countDown();
                return approvalService.decide(provinceUserId, termination.id(), true, null);
            });
            assertThat(decisionStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(decision.isDone()).isFalse();
            releaseCompletion.countDown();
            assertThat(completion.get(10, TimeUnit.SECONDS).status()).isEqualTo(DocumentStatus.AVAILABLE);
            assertThat(decision.get(10, TimeUnit.SECONDS).status()).isEqualTo(ApprovalStatus.APPROVED);
        } finally {
            releaseCompletion.countDown();
        }
        assertThat(projectRepository.findById(projectId).orElseThrow().currentStageCode())
                .isEqualTo(ProjectStage.TERMINATED.name());
    }

    @Test
    void rejectsCrossProjectCorrectionAndMakesTerminatedProjectReadOnly() throws Exception {
        var projectId = createProject();
        var otherProjectId = createProject();
        var otherProgressId = progressService.findProgress(cityUserId, otherProjectId, PageRequest.of(0, 20))
                .getContent().getFirst().id();
        var foreignCorrection = catchThrowable(() -> progressService.append(cityUserId, projectId,
                new AppendProgressCommand("错误更正", "继续联系", otherProgressId)));
        assertThat(foreignCorrection).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) foreignCorrection).errorCode()).isEqualTo(ProjectErrorCode.PROGRESS_NOT_FOUND);
        var databaseCrossProject = catchThrowable(() -> jdbc.update("""
                INSERT INTO project_progress (
                    id, project_id, progress_content, next_action, occurred_at, actor_id, corrects_progress_id
                ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), '跨项目更正', '不应保存', NOW(6), UUID_TO_BIN(?), UUID_TO_BIN(?))
                """, UUID.randomUUID().toString(), projectId.toString(), cityUserId.toString(), otherProgressId.toString()));
        assertThat(databaseCrossProject).isInstanceOf(DataIntegrityViolationException.class);

        var otherCityId = UUID.randomUUID();
        var otherCityUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '其他地市', true)",
                otherCityId.toString(), provinceId.toString(), otherCityId.toString().replace("-", ""));
        jdbc.update("INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled) VALUES (UUID_TO_BIN(?), ?, '其他地市人员', UUID_TO_BIN(?), UUID_TO_BIN(?), true)",
                otherCityUserId.toString(), otherCityUserId.toString(), provinceId.toString(), otherCityId.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), 'CITY_REPORTER')",
                otherCityUserId.toString());
        var otherCityAppend = catchThrowable(() -> progressService.append(otherCityUserId, projectId,
                new AppendProgressCommand("跨市进展", "不应保存", null)));
        assertThat(otherCityAppend).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) otherCityAppend).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
        var otherCityRead = catchThrowable(() -> projectService.findDetail(otherCityUserId, projectId));
        assertThat(otherCityRead).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) otherCityRead).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);

        var adminId = UUID.randomUUID();
        addUser(adminId, "SYSTEM_ADMIN", false);
        var adminAppend = catchThrowable(() -> progressService.append(adminId, projectId,
                new AppendProgressCommand("管理员进展", "继续联系", null)));
        assertThat(adminAppend).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) adminAppend).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);

        var termination = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户终止", null, null, null));
        approvalService.decide(provinceUserId, termination.id(), true, null);
        var terminatedAppend = catchThrowable(() -> progressService.append(interfaceUserId, projectId,
                new AppendProgressCommand("终止后补写", "不应保存", null)));
        assertThat(terminatedAppend).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) terminatedAppend).errorCode()).isEqualTo(ProjectErrorCode.TERMINATED_READ_ONLY);
        assertThat(progressService.findProgress(interfaceUserId, projectId, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(1);
        assertThat(progressService.findMilestones(interfaceUserId, projectId, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(2);
        mockMvc.perform(post("/api/v1/projects/{projectId}/progress", projectId)
                        .header("X-NDI-Actor-Id", interfaceUserId.toString())
                        .contentType("application/json")
                        .content("{\"progressContent\":\"再次补写\",\"nextAction\":\"不应保存\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_TERMINATED_READ_ONLY"));
    }

    private UUID createProject() {
        return projectService.createProject(cityUserId, new CreateProjectCommand(
                provinceId, cityId, "原项目", "客户", "C25", "客户需要系统升级",
                new BigDecimal("100.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "已开展交流", "准备下一次沟通"
        )).id();
    }

    private void setSignedProject(UUID projectId, String contractNo) {
        jdbc.update("UPDATE project_project SET current_stage_code = 'COLLECTION', contract_no = ?, signed_amount = 80.00, version_no = version_no + 1 WHERE id = UUID_TO_BIN(?)",
                contractNo, projectId.toString());
    }

    private void forceSigned(UUID projectId) {
        jdbc.update("UPDATE project_project SET current_stage_code = 'SIGNED', version_no = version_no + 1 WHERE id = UUID_TO_BIN(?)",
                projectId.toString());
    }

    private UUID createAvailableDocument(UUID projectId, String purpose) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO document_object (
                    id, scope_type, scope_id, purpose_code, bucket_name, staging_object_key, object_key,
                    original_name, media_type, byte_size, sha256_hex, status, uploaded_by, created_at, completed_at
                ) VALUES (
                    UUID_TO_BIN(?), 'PROJECT', UUID_TO_BIN(?), ?, 'ndi-documents', ?, ?,
                    '材料.pdf', 'application/pdf', 3, ?, 'AVAILABLE', UUID_TO_BIN(?), NOW(6), NOW(6)
                )
                """, id.toString(), projectId.toString(), purpose,
                "staging/" + id, "objects/" + id, "0".repeat(64), cityUserId.toString());
        return id;
    }

    private void addUser(UUID userId, String roleCode, boolean cityUser) {
        jdbc.update("INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled) VALUES (UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?, true)",
                userId.toString(), userId.toString(), roleCode, provinceId.toString(),
                cityUser ? uuidBytes(cityId) : null);
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), ?)",
                userId.toString(), roleCode);
    }

    private byte[] uuidBytes(UUID value) {
        var buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
