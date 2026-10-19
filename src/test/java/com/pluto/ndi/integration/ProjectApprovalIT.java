package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.ApprovalNotificationKind;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.mockito.Mockito.when;

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
