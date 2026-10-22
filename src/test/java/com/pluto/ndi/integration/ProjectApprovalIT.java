package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.pluto.ndi.NdiApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.DocumentDirectory;
import com.pluto.ndi.documents.service.ExportFileDirectory;
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
import com.pluto.ndi.projects.domain.ProjectNarrative;
import com.pluto.ndi.projects.domain.ProjectExportStatus;
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
import com.pluto.ndi.projects.service.ProjectActivityService;
import com.pluto.ndi.projects.service.ProjectMapService;
import com.pluto.ndi.projects.service.ProjectMapBoundaryClient;
import com.pluto.ndi.projects.service.AppendProgressCommand;
import com.pluto.ndi.projects.service.UpdateProjectProfileCommand;
import com.pluto.ndi.projects.service.ProjectService;
import com.pluto.ndi.projects.service.ProjectQuery;
import com.pluto.ndi.projects.service.ProjectExportService;
import com.pluto.ndi.projects.service.ProjectExportWorker;
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
import org.mockito.ArgumentCaptor;
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
        registry.add("ndi.projects.export.initial-delay-ms", () -> "600000");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
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
    @Autowired private ProjectActivityService activityService;
    @Autowired private ProjectMapService mapService;
    @Autowired private ProjectExportService exportService;
    @Autowired private ProjectExportWorker exportWorker;
    @MockitoBean private DocumentDirectory documentDirectory;
    @MockitoBean private ExportFileDirectory exportFileDirectory;
    @MockitoBean private ProjectMapBoundaryClient mapBoundaryClient;

    private UUID provinceId;
    private UUID cityId;
    private UUID cityUserId;
    private UUID provinceUserId;
    private UUID interfaceUserId;
    private int createdProjectCount;

    @BeforeEach
    void seedIdentity() {
        createdProjectCount = 0;
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
    void draftIsPrivateAndConsumedWhenProjectIsCreated() throws Exception {
        var response = mockMvc.perform(post("/api/v1/projects/drafts")
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json")
                        .content("{\"title\":\"待完善项目\",\"data\":{\"projectName\":\"待完善项目\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var draftId = UUID.fromString(json.readTree(response).path("data").path("id").asText());
        mockMvc.perform(get("/api/v1/projects/drafts")
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/projects/drafts")
                        .header("X-NDI-Actor-Id", provinceUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(put("/api/v1/projects/drafts/{draftId}", draftId)
                        .header("X-NDI-Actor-Id", provinceUserId.toString())
                        .contentType("application/json")
                        .content("{\"title\":\"不允许\",\"data\":{}}"))
                .andExpect(status().isNotFound());

        var foreignDraft = catchThrowable(() -> projectService.createProject(provinceUserId,
                new CreateProjectCommand(provinceId, cityId, "不能消费别人草稿", "客户", "C25", "客户需求",
                        new BigDecimal("100.00"), provinceUserId, "客户经理",
                        List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                        "首次交流", "下一步", draftId, false, null)));
        assertThat(((BusinessException) foreignDraft).errorCode()).isEqualTo(ProjectErrorCode.DRAFT_NOT_FOUND);

        projectService.createProject(cityUserId, new CreateProjectCommand(
                provinceId, cityId, "草稿正式项目", "客户", "C25", "客户需求",
                new BigDecimal("100.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "首次交流", "下一步", draftId, false, null));
        mockMvc.perform(get("/api/v1/projects/drafts")
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void preventsSameCityProjectWithNormalizedName() throws Exception {
        createProject();
        mockMvc.perform(get("/api/v1/projects/duplicate-check")
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("cityId", cityId.toString())
                        .param("projectName", "原－项目"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("STRONG_DUPLICATE"));
        var failure = catchThrowable(() -> projectService.createProject(cityUserId,
                new CreateProjectCommand(provinceId, cityId, "原－项目", "客户", "C25", "客户需求",
                        new BigDecimal("100.00"), provinceUserId, "客户经理",
                        List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                        "首次交流", "下一步")));
        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.DUPLICATE_PROJECT);
    }

    @Test
    void conflictingNameChangeDoesNotOverwriteExistingProject() {
        var firstId = createProject();
        var secondId = createProject();
        var request = approvalService.submitMajorChange(cityUserId, firstId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "拟改名", "原－项目-2", null, null));

        var decided = approvalService.decide(provinceUserId, request.id(), true, null);

        assertThat(decided.status()).isEqualTo(ApprovalStatus.CONFLICT);
        assertThat(projectRepository.findById(firstId).orElseThrow().projectName()).isEqualTo("原项目");
        assertThat(projectRepository.findById(secondId).orElseThrow().projectName()).isEqualTo("原项目-2");
    }

    @Test
    void signingSubjectDefaultsToProvinceAndAcceptsDigitalServices() {
        var defaultId = createProject();
        assertThat(projectRepository.findById(defaultId).orElseThrow().signingSubjectCode())
                .isEqualTo("PROVINCE_SIGN");

        var digital = projectService.createProject(cityUserId, new CreateProjectCommand(
                provinceId, cityId, "数科签约项目", "客户", "C25", "客户需求",
                new BigDecimal("100.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "首次交流", "下一步", null, false, null, "DS_SIGN"));
        assertThat(projectRepository.findById(digital.id()).orElseThrow().signingSubjectCode())
                .isEqualTo("DS_SIGN");

        var invalid = catchThrowable(() -> projectService.createProject(cityUserId, new CreateProjectCommand(
                provinceId, cityId, "错误签约主体项目", "客户", "C25", "客户需求",
                new BigDecimal("100.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "首次交流", "下一步", null, false, null, "UNKNOWN")));
        assertThat(((BusinessException) invalid).errorCode()).isEqualTo(ProjectErrorCode.INVALID_SIGNING_SUBJECT);
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
        assertThat(project.opportunityDate()).isEqualTo(project.updatedAt());
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
        assertThat(original.progressDate()).isNotNull();
        assertThat(original.ownerName()).isEqualTo("PROVINCE_REPORTER");
        var version = projectRepository.findById(projectId).orElseThrow().versionNo();
        var request = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.PROJECT_NAME, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户正式名称变化", "更新后的名称", null, null));

        var added = progressService.append(interfaceUserId, projectId,
                new AppendProgressCommand("  接口人补充进展  ", "下一步联络客户", null));
        var correction = progressService.append(provinceUserId, projectId,
                new AppendProgressCommand("原始交流记录更正", "重新确认需求", original.id()));

        assertThat(added.progressContent()).isEqualTo("接口人补充进展");
        assertThat(added.progressDate()).isNotNull();
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
                        .content("{\"progressContent\":\"接口新增进展\",\"nextAction\":\"继续跟进\","
                                + "\"progressDate\":\"2026-06-18\",\"ownerName\":\" 张经理 \","
                                + "\"dueDate\":\"2026-06-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.progressContent").value("接口新增进展"))
                .andExpect(jsonPath("$.data.progressDate").value("2026-06-18"))
                .andExpect(jsonPath("$.data.ownerName").value("张经理"))
                .andExpect(jsonPath("$.data.dueDate").value("2026-06-30"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/progress", projectId)
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json")
                        .content("{\"progressContent\":\"沿用旧客户端\",\"nextAction\":\"继续跟进\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.progressDate").isNotEmpty())
                .andExpect(jsonPath("$.data.ownerName").doesNotExist());
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
                "  客户增加上线要求  ", provinceUserId, "  新客户经理  ",
                List.of(" 张专家 ", "李专家"));

        var updated = profileService.update(cityUserId, projectId, command);
        assertThat(updated.versionNo()).isEqualTo(original.versionNo() + 1);
        assertThat(updated.customerName()).isEqualTo("新客户");
        assertThat(updated.customerNeed()).isEqualTo("客户增加上线要求");
        assertThat(updated.cityAccountManagerName()).isEqualTo("新客户经理");
        assertThat(updated.productSupportExpertNames()).containsExactly("张专家", "李专家");
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
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_support_expert WHERE project_id = UUID_TO_BIN(?)
                """, Integer.class, projectId.toString())).isEqualTo(2);

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
    void changesOnlyExpertNamesAndCanClearThem() {
        var projectId = createProject();
        var original = projectService.findDetail(cityUserId, projectId);
        var withExperts = profileService.update(cityUserId, projectId,
                new UpdateProjectProfileCommand(original.versionNo(), original.customerName(),
                        original.industryCode(), original.customerNeed(), original.provinceLeaderUserId(),
                        original.cityAccountManagerName(), List.of("张专家", "李专家")));
        assertThat(withExperts.versionNo()).isEqualTo(original.versionNo() + 1);
        assertThat(withExperts.productSupportExpertNames()).containsExactly("张专家", "李专家");

        var cleared = profileService.update(cityUserId, projectId,
                new UpdateProjectProfileCommand(withExperts.versionNo(), withExperts.customerName(),
                        withExperts.industryCode(), withExperts.customerNeed(), withExperts.provinceLeaderUserId(),
                        withExperts.cityAccountManagerName(), List.of()));
        assertThat(cleared.versionNo()).isEqualTo(withExperts.versionNo() + 1);
        assertThat(cleared.productSupportExpertNames()).isEmpty();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_support_expert WHERE project_id = UUID_TO_BIN(?)
                """, Integer.class, projectId.toString())).isZero();
    }

    @Test
    void createsAndMaintainsPlanningAttributesWithVersionedAudit() {
        var created = projectService.createProject(cityUserId, new CreateProjectCommand(
                provinceId, cityId, "规划项目-" + UUID.randomUUID(), "客户", "C25", "客户需求",
                new BigDecimal("100.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "完成初步交流", "继续讨论", null, true, "独立规划项目", "PROVINCE_SIGN",
                List.of(), "2026-11", List.of("FLEXIBLE_PRODUCTION", "QUALITY_TRACEABILITY")));
        var original = projectService.findDetail(cityUserId, created.id());
        assertThat(original.plannedBidMonth()).isEqualTo(java.time.LocalDate.of(2026, 11, 1));
        assertThat(original.applicationScenarioCodes())
                .containsExactly("FLEXIBLE_PRODUCTION", "QUALITY_TRACEABILITY");

        var updated = profileService.update(cityUserId, created.id(), new UpdateProjectProfileCommand(
                original.versionNo(), original.customerName(), original.industryCode(), original.customerNeed(),
                original.provinceLeaderUserId(), original.cityAccountManagerName(),
                original.productSupportExpertNames(), "", List.of("ENERGY_CONTROL")));
        assertThat(updated.versionNo()).isEqualTo(original.versionNo() + 1);
        assertThat(updated.plannedBidMonth()).isNull();
        assertThat(updated.applicationScenarioCodes()).containsExactly("ENERGY_CONTROL");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_application_scenario WHERE project_id = UUID_TO_BIN(?)
                """, Integer.class, created.id().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_change_event
                WHERE project_id = UUID_TO_BIN(?) AND event_type = 'PROJECT_PROFILE_UPDATED'
                """, Integer.class, created.id().toString())).isEqualTo(1);

        var invalid = catchThrowable(() -> profileService.update(cityUserId, created.id(),
                new UpdateProjectProfileCommand(updated.versionNo(), updated.customerName(),
                        updated.industryCode(), updated.customerNeed(), updated.provinceLeaderUserId(),
                        updated.cityAccountManagerName(), updated.productSupportExpertNames(),
                        "2026-13", List.of())));
        assertThat(((BusinessException) invalid).errorCode()).isEqualTo(ProjectErrorCode.INVALID_PLANNING_FIELDS);
    }

    @Test
    void preservesNarrativeThroughCreateAndVersionedProfileUpdate() {
        var created = projectService.createProject(cityUserId, new CreateProjectCommand(
                provinceId, cityId, "资料项目-" + UUID.randomUUID(), "客户", "C25", "客户需求",
                new BigDecimal("100.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "完成初步交流", "继续讨论", null, true, "独立资料项目", "PROVINCE_SIGN",
                List.of(), null, List.of(), new ProjectNarrative(" 原始建设 ", " 产品方案 ", " 备注 ")));
        var original = projectService.findDetail(cityUserId, created.id());
        assertThat(original.constructionContentRaw()).isEqualTo("原始建设");
        assertThat(original.productPromotionContent()).isEqualTo("产品方案");
        assertThat(original.remark()).isEqualTo("备注");

        var updated = profileService.update(cityUserId, created.id(), new UpdateProjectProfileCommand(
                original.versionNo(), original.customerName(), original.industryCode(), original.customerNeed(),
                original.provinceLeaderUserId(), original.cityAccountManagerName(),
                original.productSupportExpertNames(), null, null,
                new ProjectNarrative("", "新产品方案", "")));
        assertThat(updated.versionNo()).isEqualTo(original.versionNo() + 1);
        assertThat(updated.constructionContentRaw()).isEqualTo("客户需求");
        assertThat(updated.productPromotionContent()).isEqualTo("新产品方案");
        assertThat(updated.remark()).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_change_event
                WHERE project_id = UUID_TO_BIN(?) AND event_type = 'PROJECT_PROFILE_UPDATED'
                """, Integer.class, created.id().toString())).isEqualTo(1);

        var invalid = catchThrowable(() -> profileService.update(cityUserId, created.id(),
                new UpdateProjectProfileCommand(updated.versionNo(), updated.customerName(),
                        updated.industryCode(), updated.customerNeed(), updated.provinceLeaderUserId(),
                        updated.cityAccountManagerName(), null, null, null,
                        new ProjectNarrative(null, "x".repeat(4001), null))));
        assertThat(((BusinessException) invalid).errorCode()).isEqualTo(ProjectErrorCode.INVALID_NARRATIVE);
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
    void recentActivitiesRespectCityScopeAndExcludePendingChanges() throws Exception {
        var projectId = createProject();
        var secondCityId = UUID.randomUUID();
        var secondCityUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '第二地市', true)",
                secondCityId.toString(), provinceId.toString(), secondCityId.toString().replace("-", ""));
        addUserInProvince(secondCityUserId, provinceId, secondCityId, "CITY_REPORTER");
        var otherProjectId = projectService.createProject(secondCityUserId, new CreateProjectCommand(
                provinceId, secondCityId, "异地项目", "客户", "C25", "升级需求",
                new BigDecimal("90.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("30.00"))),
                "初次交流", "继续跟进")).id();
        progressService.append(cityUserId, projectId, new AppendProgressCommand("推进方案", "准备招标", null));
        var pending = approvalService.submitMajorChange(cityUserId, projectId, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户追加预算", null, new BigDecimal("120.00"), null));

        var beforeApproval = activityService.recent(cityUserId);
        assertThat(beforeApproval).hasSize(2);
        assertThat(beforeApproval).extracting(item -> item.projectId()).containsOnly(projectId);
        assertThat(beforeApproval).extracting(item -> item.summary())
                .contains("追加项目进展").noneMatch(item -> item.contains("重大变更"));
        assertThat(activityService.recent(provinceUserId)).extracting(item -> item.projectId())
                .contains(projectId, otherProjectId);

        approvalService.decide(provinceUserId, pending.id(), true, null);
        mockMvc.perform(get("/api/v1/projects/workbench/recent-activities")
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.data[0].summary").value("重大变更审批通过"));
        assertThat(activityService.recent(secondCityUserId)).extracting(item -> item.projectId())
                .containsOnly(otherProjectId);
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
        assertThat(mapService.current(cityUserId, null, false, null).regions())
                .filteredOn(region -> region.id().equals(otherCityId))
                .singleElement().satisfies(region -> {
                    assertThat(region.authorized()).isFalse();
                    assertThat(region.projectCount()).isZero();
                    assertThat(region.expectedAmount()).isEqualByComparingTo("0");
                    assertThat(region.categoryTotals().network()).isZero();
                });
        assertThat(mapService.current(provinceUserId, null, false, null).regions())
                .filteredOn(region -> region.id().equals(otherCityId))
                .singleElement().satisfies(region -> {
                    assertThat(region.expectedAmount()).isEqualByComparingTo("70.00");
                    assertThat(region.categoryTotals().network()).isEqualByComparingTo("30.00");
                });
        assertThat(workbenchService.overview(interfaceUserId, false).totalExpectedAmount())
                .isEqualByComparingTo("170.00");

        var adminId = UUID.randomUUID();
        addUser(adminId, "SYSTEM_ADMIN", false);
        assertThat(workbenchService.overview(adminId, false).projectCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void nationalReadersCanViewAcrossProvincesWithoutBusinessWritePermission() throws Exception {
        var localProjectId = createProject();
        var otherProvinceId = UUID.randomUUID();
        var otherCityId = UUID.randomUUID();
        var otherLeaderId = UUID.randomUUID();
        var otherCityUserId = UUID.randomUUID();
        var groupUserId = UUID.randomUUID();
        var adminUserId = UUID.randomUUID();
        var otherProjectName = "跨省项目-" + otherCityId;
        jdbc.update("INSERT INTO organization_province (id, province_code, province_name, enabled) VALUES (UUID_TO_BIN(?), ?, ?, true)",
                otherProvinceId.toString(), otherProvinceId.toString().replace("-", ""), "跨省测试省分");
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, true)",
                otherCityId.toString(), otherProvinceId.toString(), otherCityId.toString().replace("-", ""), "跨省测试地市");
        addUserInProvince(otherLeaderId, otherProvinceId, null, "PROVINCE_REPORTER");
        addUserInProvince(otherCityUserId, otherProvinceId, otherCityId, "CITY_REPORTER");
        var otherProjectId = projectService.createProject(otherCityUserId, new CreateProjectCommand(
                otherProvinceId, otherCityId, otherProjectName, "客户", "C25", "跨省需求",
                new BigDecimal("70.00"), otherLeaderId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("30.00"))),
                "完成初次沟通", "继续跟进")).id();
        addUserInProvince(groupUserId, null, null, "GROUP_REVIEWER");
        addUserInProvince(adminUserId, null, null, "SYSTEM_ADMIN");

        var otherProvinceQuery = new ProjectQuery(null, otherProjectName, null, null,
                otherProvinceId, null, true);
        assertThat(projectService.findProjects(groupUserId, otherProvinceQuery, PageRequest.of(0, 20))
                .getContent()).extracting(project -> project.id()).containsExactly(otherProjectId);
        assertThat(projectService.findProjects(adminUserId, otherProvinceQuery, PageRequest.of(0, 20))
                .getContent()).extracting(project -> project.id()).containsExactly(otherProjectId);
        assertThat(projectService.findProjects(provinceUserId, otherProvinceQuery, PageRequest.of(0, 20))
                .getContent()).isEmpty();
        assertThat(projectService.findDetail(groupUserId, localProjectId).id()).isEqualTo(localProjectId);
        assertThat(projectService.findDetail(groupUserId, otherProjectId).id()).isEqualTo(otherProjectId);
        for (var role : List.of("DS_TECH", "DS_INTELLIGENCE", "GD_DPASS")) {
            var nationalUserId = UUID.randomUUID();
            addUserInProvince(nationalUserId, null, null, role);
            assertThat(projectService.findDetail(nationalUserId, otherProjectId).id()).isEqualTo(otherProjectId);
        }
        assertThat(workbenchService.breakdown(groupUserId, false).cities())
                .extracting(city -> city.cityId()).contains(cityId, otherCityId);
        var map = mapService.current(groupUserId, null, false, null);
        assertThat(map.level()).isEqualTo("NATIONAL");
        assertThat(map.regions()).filteredOn(region -> region.id().equals(provinceId)
                || region.id().equals(otherProvinceId))
                .extracting(region -> region.projectCount()).containsExactlyInAnyOrder(1L, 1L);
        assertThat(mapService.current(cityUserId, null, false, null).level()).isEqualTo("PROVINCE");
        var foreignMap = catchThrowable(() -> mapService.current(cityUserId, otherProvinceId, false, null));
        assertThat(((BusinessException) foreignMap).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
        when(mapBoundaryClient.fetch("100000"))
                .thenReturn("{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/v1/projects/workbench/map/boundaries/100000")
                        .header("X-NDI-Actor-Id", groupUserId.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/workbench/map/boundaries/100000")
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/workbench/map/boundaries/not-a-code")
                        .header("X-NDI-Actor-Id", groupUserId.toString()))
                .andExpect(status().isBadRequest());
        assertThat(workbenchService.overview(adminUserId, false).projectCount())
                .isGreaterThan(workbenchService.overview(provinceUserId, false).projectCount());
        var export = exportService.submit(groupUserId, otherProvinceQuery);
        assertThat(exportService.find(groupUserId, export.id()).status()).isEqualTo(ProjectExportStatus.PENDING);
        jdbc.update("DELETE FROM project_export_task WHERE id = UUID_TO_BIN(?)", export.id().toString());
        var denied = catchThrowable(() -> progressService.append(groupUserId, otherProjectId,
                new AppendProgressCommand("不可写入", "不可写入", null)));
        assertThat(((BusinessException) denied).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
        jdbc.update("UPDATE project_project SET current_stage_code = 'TERMINATED' WHERE id = UUID_TO_BIN(?)",
                localProjectId.toString());
        assertThat(mapService.current(groupUserId, null, false, null).regions())
                .filteredOn(region -> region.id().equals(provinceId))
                .singleElement().satisfies(region -> assertThat(region.projectCount()).isZero());
        assertThat(mapService.current(groupUserId, null, true, ProjectStage.TERMINATED).regions())
                .filteredOn(region -> region.id().equals(provinceId))
                .singleElement().satisfies(region -> assertThat(region.projectCount()).isEqualTo(1));
    }

    @Test
    void workbenchBreakdownKeepsScopeTotalsAndShowsAssignedApprovalBadge() throws Exception {
        var activeId = createProject();
        var terminatedId = createProject();
        var otherCityId = UUID.randomUUID();
        var otherCityUserId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '第二地市', true)",
                otherCityId.toString(), provinceId.toString(), otherCityId.toString().replace("-", ""));
        jdbc.update("INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled) VALUES (UUID_TO_BIN(?), ?, '第二地市人员', UUID_TO_BIN(?), UUID_TO_BIN(?), true)",
                otherCityUserId.toString(), otherCityUserId.toString(), provinceId.toString(), otherCityId.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), 'CITY_REPORTER')",
                otherCityUserId.toString());
        projectService.createProject(otherCityUserId, new CreateProjectCommand(
                provinceId, otherCityId, "第二地市项目", "客户", "C26", "化工升级需求",
                new BigDecimal("70.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("GEWU", new BigDecimal("30.00"))),
                "已开展交流", "继续跟进"));

        var termination = approvalService.submitMajorChange(cityUserId, terminatedId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户终止项目", null, null, null));
        approvalService.decide(provinceUserId, termination.id(), true, null);
        var pending = approvalService.submitMajorChange(cityUserId, activeId, new MajorChangeCommand(
                MajorChangeType.TOTAL_AMOUNT, ProjectInitiatorLevel.CITY, provinceUserId,
                "预算调整", null, new BigDecimal("120.00"), null));

        var province = workbenchService.breakdown(provinceUserId, false);
        assertThat(province.cities()).hasSize(2);
        assertThat(province.industries()).hasSize(2);
        assertThat(province.cities().stream().mapToLong(item -> item.projectCount()).sum()).isEqualTo(2);
        assertThat(province.industries().stream().map(item -> item.expectedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("170.00");
        assertThat(province.industries().getFirst().industryName()).isEqualTo("石油、煤炭及其他燃料加工业");
        assertThat(province.cities().getFirst().cityName()).isEqualTo("测试地市");
        assertThat(workbenchService.breakdown(cityUserId, false).cities()).hasSize(1);
        assertThat(workbenchService.breakdown(otherCityUserId, false).industries()).extracting("industryCode")
                .containsExactly("C26");
        assertThat(workbenchService.breakdown(otherCityUserId, false).cities()).extracting("cityId")
                .containsExactly(otherCityId);
        assertThat(workbenchService.breakdown(provinceUserId, true).industries().getFirst().expectedAmount())
                .isEqualByComparingTo("200.00");

        var approverOverview = workbenchService.overview(provinceUserId, false);
        assertThat(approverOverview.approvals().pendingCount()).isEqualTo(1);
        assertThat(approverOverview.approvals().overdueCount()).isZero();
        assertThat(workbenchService.overview(cityUserId, false).approvals().pendingCount()).isZero();
        jdbc.update("UPDATE project_approval_request SET due_at = DATE_SUB(NOW(6), INTERVAL 1 HOUR) WHERE id = UUID_TO_BIN(?)",
                pending.id().toString());
        assertThat(workbenchService.overview(provinceUserId, false).approvals().overdueCount()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/projects/workbench/breakdown")
                        .header("X-NDI-Actor-Id", provinceUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities.length()").value(2))
                .andExpect(jsonPath("$.data.industries.length()").value(2));
        mockMvc.perform(get("/api/v1/projects/workbench/breakdown")
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cities.length()").value(1));
        mockMvc.perform(get("/api/v1/projects/workbench/overview")
                        .header("X-NDI-Actor-Id", provinceUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvals.pendingCount").value(1))
                .andExpect(jsonPath("$.data.approvals.overdueCount").value(1))
                .andExpect(jsonPath("$.data.approvals.inboxApiPath").value("/api/v1/projects/approvals/inbox"));
        approvalService.decide(provinceUserId, pending.id(), true, null);
    }

    @Test
    void projectListCombinesFiltersWithActorScope() throws Exception {
        var activeId = createProject();
        var terminatedId = createProject();
        var termination = approvalService.submitMajorChange(cityUserId, terminatedId, new MajorChangeCommand(
                MajorChangeType.TERMINATE, ProjectInitiatorLevel.CITY, provinceUserId,
                "客户停止项目", null, null, null));
        approvalService.decide(provinceUserId, termination.id(), true, null);
        var activeCode = projectRepository.findById(activeId).orElseThrow().projectCode();

        mockMvc.perform(get("/api/v1/projects")
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("projectName", "原项目")
                        .param("industryCode", "c25")
                        .param("includeTerminated", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(activeId.toString()));
        mockMvc.perform(get("/api/v1/projects")
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("stage", "TERMINATED")
                        .param("projectCode", activeCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/projects")
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("provinceId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        jdbc.update("UPDATE project_project SET project_name = '进度%目标' WHERE id = UUID_TO_BIN(?)",
                activeId.toString());
        mockMvc.perform(get("/api/v1/projects")
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("projectName", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void exportUsesScopedQueryAndAuditsDownload() throws Exception {
        createProject();
        var response = mockMvc.perform(post("/api/v1/projects/exports")
                        .header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json")
                        .content("{\"projectName\":\"原项目\",\"industryCode\":\"C25\",\"includeTerminated\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        var taskId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("data").path("id").asText());
        exportWorker.runPending();
        assertThat(exportService.find(cityUserId, taskId).status()).isEqualTo(ProjectExportStatus.COMPLETED);
        var csvBytes = ArgumentCaptor.forClass(byte[].class);
        verify(exportFileDirectory).storeCsv(eq(taskId), csvBytes.capture());
        assertThat(new String(csvBytes.getValue(), StandardCharsets.UTF_8)).contains("原项目");

        when(exportFileDirectory.signCsvDownload(taskId)).thenReturn("https://storage.example/export");
        mockMvc.perform(get("/api/v1/projects/exports/{exportId}/download", taskId)
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://storage.example/export"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_export_download WHERE export_id = UUID_TO_BIN(?)",
                Integer.class, taskId.toString())).isEqualTo(1);
        mockMvc.perform(get("/api/v1/projects/exports/{exportId}/download", taskId)
                        .header("X-NDI-Actor-Id", provinceUserId.toString()))
                .andExpect(status().isForbidden());

        var anotherCityId = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id, province_id, city_code, city_name, enabled) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, '调岗地市', true)",
                anotherCityId.toString(), provinceId.toString(), anotherCityId.toString().replace("-", ""));
        jdbc.update("UPDATE identity_user_account SET city_id = UUID_TO_BIN(?) WHERE id = UUID_TO_BIN(?)",
                anotherCityId.toString(), cityUserId.toString());
        mockMvc.perform(get("/api/v1/projects/exports/{exportId}/download", taskId)
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void advancedFiltersShareLiteralMatchingScopeAndExportWithoutDuplicateExperts() throws Exception {
        var projectId = createProject();
        jdbc.update("UPDATE project_project SET signing_subject_code='DS_SIGN', city_account_manager_name='客户%_经理' WHERE id=UUID_TO_BIN(?)", projectId.toString());
        jdbc.update("UPDATE identity_user_account SET display_name='省分%_负责人' WHERE id=(SELECT province_leader_user_id FROM project_project WHERE id=UUID_TO_BIN(?))", projectId.toString());
        jdbc.update("INSERT INTO project_support_expert(project_id,sort_order,expert_name) VALUES(UUID_TO_BIN(?),0,'测试%_专家甲'),(UUID_TO_BIN(?),1,'测试%_专家乙')", projectId.toString(), projectId.toString());
        var filter = new ProjectQuery(null, null, null, null, null, null, true,
                " ds_sign ", " %_ ", "客户%_", "测试%_", "原项目");
        assertThat(projectService.findProjects(cityUserId, filter, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements()).isEqualTo(1);
        assertThat(projectService.countProjects(cityUserId, filter)).isEqualTo(1);
        for (var field : List.of("provinceLeaderName", "cityAccountManagerName", "productSupportExpertName", "keyword")) {
            mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                            .param(field, "不匹配的条件"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        }
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("signingSubjectCode", "PROVINCE_SIGN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("signingSubjectCode", "DS_SIGN").param("provinceLeaderName", "%_")
                        .param("cityAccountManagerName", "客户%_").param("productSupportExpertName", "测试%_")
                        .param("keyword", "原项目").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(projectId.toString()));
        var task = exportService.submit(cityUserId, filter);
        exportWorker.runPending();
        assertThat(exportService.find(cityUserId, task.id()).rowCount()).isEqualTo(1);
        var csv = ArgumentCaptor.forClass(byte[].class);
        verify(exportFileDirectory).storeCsv(eq(task.id()), csv.capture());
        assertThat(new String(csv.getValue(), StandardCharsets.UTF_8)).contains("原项目");
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("provinceLeaderName", "不匹配的负责人"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("signingSubjectCode", "INVALID"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/projects/exports").header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json").content("{\"signingSubjectCode\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        var otherCity = UUID.randomUUID();
        var otherReporter = UUID.randomUUID();
        jdbc.update("INSERT INTO organization_city (id,province_id,city_code,city_name,enabled) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,'筛选隔离市',true)", otherCity.toString(), provinceId.toString(), otherCity.toString().replace("-", ""));
        addUserInProvince(otherReporter, provinceId, otherCity, "CITY_REPORTER");
        assertThat(projectService.countProjects(otherReporter, filter)).isZero();
    }

    @Test
    void administratorCanExportButCannotCreateEvenWithProvinceReporterRole() {
        var administratorId = UUID.randomUUID();
        addUser(administratorId, "PROVINCE_REPORTER", false);
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), 'SYSTEM_ADMIN')",
                administratorId.toString());
        var export = exportService.submit(administratorId, ProjectQuery.all());
        assertThat(export.status()).isEqualTo(ProjectExportStatus.PENDING);
        jdbc.update("DELETE FROM project_export_task WHERE id = UUID_TO_BIN(?)", export.id().toString());
        var denied = catchThrowable(() -> projectService.createProject(administratorId, new CreateProjectCommand(
                provinceId, cityId, "管理员不得创建", "客户", "C25", "客户需求",
                new BigDecimal("100.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("20.00"))),
                "项目交流", "继续跟进")));
        assertThat(((BusinessException) denied).errorCode()).isEqualTo(ProjectErrorCode.CREATION_FORBIDDEN);
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
    void documentDeletionChecksPermissionAndProtectsApprovalEvidence() throws Exception {
        var projectId = createProject();
        var generalId = createAvailableDocument(projectId, "GENERAL");
        var solutionId = createAvailableDocument(projectId, "CUSTOMER_SOLUTION");
        var nationalReader = UUID.randomUUID();
        addUserInProvince(nationalReader, null, null, "GROUP_REVIEWER");
        when(documentDirectory.listAvailable("PROJECT", projectId)).thenReturn(List.of(
                new DocumentSummary(generalId, projectId, "GENERAL", "资料.pdf", "application/pdf",
                        3, "0".repeat(64), DocumentStatus.AVAILABLE, Instant.now())));
        assertThat(projectDocumentService.listAvailable(nationalReader, projectId)).hasSize(1);
        mockMvc.perform(delete("/api/v1/projects/{projectId}/documents/{documentId}", projectId, generalId)
                        .header("X-NDI-Actor-Id", nationalReader.toString()))
                .andExpect(status().isForbidden());

        when(documentDirectory.isUsable("PROJECT", projectId, solutionId, "CUSTOMER_SOLUTION")).thenReturn(true);
        stageService.submit(cityUserId, projectId, new StageAdvanceCommand(
                ProjectInitiatorLevel.CITY, ProjectStage.BIDDING, interfaceUserId,
                LocalDate.now(ZoneId.of("Asia/Shanghai")), solutionId, null, null, null, null));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/documents/{documentId}", projectId, solutionId)
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_DOCUMENT_IN_USE"));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/documents/{documentId}", projectId, generalId)
                        .header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk());
        verify(documentDirectory).delete("PROJECT", projectId, generalId);
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

    @Test
    void operationAuditIncludesActualApprovalActorsWithoutDuplicatingSuccessfulDecisions() throws Exception {
        var project = createProject();
        for (var outcome : List.of("REJECTED", "WITHDRAWN", "APPROVED")) {
            var submitted = mockMvc.perform(post("/api/v1/projects/{id}/major-changes", project)
                    .header("X-NDI-Actor-Id", cityUserId.toString()).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("changeType", "PROJECT_NAME", "initiatorLevel", "CITY",
                            "approverId", provinceUserId, "reason", "审计验证", "projectName", "审计项目-" + outcome))))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            var request = json.readTree(submitted).path("data").path("id").asText();
            if (outcome.equals("WITHDRAWN")) {
                mockMvc.perform(post("/api/v1/projects/approvals/{id}/withdraw", request)
                        .header("X-NDI-Actor-Id", cityUserId.toString())).andExpect(status().isOk());
            } else {
                mockMvc.perform(post("/api/v1/projects/approvals/{id}/decision", request)
                        .header("X-NDI-Actor-Id", provinceUserId.toString()).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("approved", outcome.equals("APPROVED"), "opinion", "审核意见"))))
                        .andExpect(status().isOk());
            }
        }
        var result = mockMvc.perform(get("/api/v1/projects/{id}/operation-audit", project)
                .header("X-NDI-Actor-Id", cityUserId.toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(7)).andReturn().getResponse().getContentAsString();
        var items = json.readTree(result).path("data").path("items");
        var types = new ArrayList<String>();
        items.forEach(item -> {
            types.add(item.path("eventType").asText());
            assertThat(item.path("operatorName").asText()).isNotBlank();
            assertThat(item.has("payload")).isFalse();
        });
        assertThat(types).contains("PROJECT_CREATED", "APPROVAL_SUBMITTED", "APPROVAL_REJECTED", "APPROVAL_WITHDRAWN", "MAJOR_CHANGE_APPROVED")
                .doesNotContain("APPROVAL_APPROVED");
        assertThat(types.stream().filter("APPROVAL_SUBMITTED"::equals).count()).isEqualTo(3);
        mockMvc.perform(get("/api/v1/projects/{id}/operation-audit", project)
                .header("X-NDI-Actor-Id", cityUserId.toString()).param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void creationAttributesRoundTripAndValidateBothOwnerFieldNames() throws Exception {
        for (var ownerField : List.of("initialProgressOwnerName", "progressOwnerName")) {
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("provinceId", provinceId);
            payload.put("cityId", cityId);
            payload.put("projectName", "属性项目-" + UUID.randomUUID());
            payload.put("customerName", "客户");
            payload.put("industryCode", "C25");
            payload.put("customerNeed", "需要升级");
            payload.put("totalAmount", 100);
            payload.put("provinceLeaderUserId", provinceUserId);
            payload.put("cityAccountManagerName", "客户经理");
            payload.put("categoryAmounts", List.of(Map.of("categoryCode", "NETWORK", "expectedAmount", 60)));
            payload.put("initialProgress", "完成交流");
            payload.put("nextAction", "下一次沟通");
            payload.put("confirmNew", true);
            payload.put("duplicateReason", "集成测试：分别验证新旧字段名");
            payload.put("importanceCode", "X".repeat(30));
            payload.put(ownerField, "人".repeat(100));
            var response = mockMvc.perform(post("/api/v1/projects")
                    .header("X-NDI-Actor-Id", cityUserId.toString()).contentType("application/json")
                    .content(json.writeValueAsBytes(payload)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.importanceCode").value("X".repeat(30)))
                    .andReturn();
            var id = UUID.fromString(json.readTree(response.getResponse().getContentAsString()).path("data").path("id").asText());
            assertThat(progressService.findProgress(cityUserId, id, PageRequest.of(0, 20))
                    .getContent().getFirst().ownerName()).isEqualTo("人".repeat(100));
            mockMvc.perform(get("/api/v1/projects/{id}", id).header("X-NDI-Actor-Id", cityUserId.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.importanceCode").value("X".repeat(30)));
            mockMvc.perform(get("/api/v1/projects").param("keyword", payload.get("projectName").toString())
                    .header("X-NDI-Actor-Id", cityUserId.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].importanceCode").value("X".repeat(30)));
            payload.put("projectName", "拒绝属性-" + UUID.randomUUID());
            payload.put(ownerField, "人".repeat(101));
            mockMvc.perform(post("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                    .contentType("application/json").content(json.writeValueAsBytes(payload)))
                    .andExpect(status().isBadRequest());
            payload.put(ownerField, "负责人");
            payload.put("importanceCode", "X".repeat(31));
            mockMvc.perform(post("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                    .contentType("application/json").content(json.writeValueAsBytes(payload)))
                    .andExpect(status().isBadRequest());
        }
    }

    private UUID createProject() {
        createdProjectCount += 1;
        var projectName = createdProjectCount == 1 ? "原项目" : "原项目-" + createdProjectCount;
        return projectService.createProject(cityUserId, new CreateProjectCommand(
                provinceId, cityId, projectName, "客户", "C25", "客户需要系统升级",
                new BigDecimal("100.00"), provinceUserId, "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))),
                "已开展交流", "准备下一次沟通", null, true, "集成测试：独立业务项目"
        )).id();
    }

    @Test
    void exportFieldsArePersistedOrderedValidatedAndEnrichedWithinScope() throws Exception {
        var project = createProject();
        jdbc.update("INSERT INTO project_support_expert(project_id,sort_order,expert_name) VALUES(UUID_TO_BIN(?),0,'专家乙'),(UUID_TO_BIN(?),1,'专家甲')", project.toString(), project.toString());
        jdbc.update("UPDATE project_activity_state SET last_activity_at='2030-01-01 00:00:00' WHERE project_id=UUID_TO_BIN(?)", project.toString());
        var fields = List.of("productSupportExpertNames", "projectName", "province", "city", "provinceLeaderName", "signingSubjectCode", "currentStageName", "cityAccountManagerName", "customerIndustry", "categories", "updatedAt");
        var result = mockMvc.perform(post("/api/v1/projects/exports").header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json").content(json.writeValueAsString(Map.of("projectName", "原项目", "fields", fields))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        var task = UUID.fromString(json.readTree(result).path("data").path("id").asText());
        assertThat(exportService.find(cityUserId, task).fields()).containsExactlyElementsOf(fields);
        exportWorker.runPending();
        var csv = ArgumentCaptor.forClass(byte[].class);
        verify(exportFileDirectory).storeCsv(eq(task), csv.capture());
        assertThat(new String(csv.getValue(), StandardCharsets.UTF_8)).startsWith("\uFEFF产品单位支撑专家,项目名称,省分,地市,省分负责人,签约主体,项目阶段,地市客户经理,客户所属行业,三化类别,最近更新时间\r\n")
                .contains("专家乙、专家甲", "PROVINCE_SIGN", "方案交流", "客户经理", "网络化", "2030-01-01T00:00:00Z");
        assertThat(exportService.find(cityUserId, task).rowCount()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/projects/exports/fields").header("X-NDI-Actor-Id", cityUserId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(22));
        for (var invalid : List.of(List.<String>of(), List.of("unknown"), List.of("projectName", "projectName"))) {
            mockMvc.perform(post("/api/v1/projects/exports").header("X-NDI-Actor-Id", cityUserId.toString())
                            .contentType("application/json").content(json.writeValueAsString(Map.of("fields", invalid))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_INVALID_EXPORT_FIELDS"));
        }
        mockMvc.perform(get("/api/v1/projects/exports/{id}", task).header("X-NDI-Actor-Id", provinceUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyTasksKeepDefaultFieldsAndInvalidTaskFieldsFailWithoutBlockingQueue() {
        createProject();
        var legacy = exportService.submit(cityUserId, ProjectQuery.all());
        jdbc.update("UPDATE project_export_task SET selected_fields=NULL WHERE id=UUID_TO_BIN(?)", legacy.id().toString());
        assertThat(exportService.find(cityUserId, legacy.id()).fields()).hasSize(11);
        exportWorker.runPending();
        assertThat(exportService.find(cityUserId, legacy.id()).status()).isEqualTo(ProjectExportStatus.COMPLETED);
        var bad = exportService.submit(cityUserId, ProjectQuery.all());
        jdbc.update("UPDATE project_export_task SET selected_fields=CAST('[\"unknown\"]' AS JSON) WHERE id=UUID_TO_BIN(?)", bad.id().toString());
        exportWorker.runPending();
        assertThat(exportService.find(cityUserId, bad.id()).status()).isEqualTo(ProjectExportStatus.FAILED);
        var good = exportService.submit(cityUserId, ProjectQuery.all(), List.of("projectName"));
        exportWorker.runPending();
        assertThat(exportService.find(cityUserId, good.id()).status()).isEqualTo(ProjectExportStatus.COMPLETED);
    }

    @Test
    void dateFiltersUseShanghaiDaysIncludeLastMicrosecondAndMatchExport() throws Exception {
        var first = createProject();
        var last = createProject();
        var next = createProject();
        var previous = createProject();
        var unknown = createProject();
        jdbc.update("UPDATE project_project SET opportunity_date=? WHERE id=UUID_TO_BIN(?)", "2026-06-30 16:00:00.000000", first.toString());
        jdbc.update("UPDATE project_project SET opportunity_date=? WHERE id=UUID_TO_BIN(?)", "2026-07-01 15:59:59.999999", last.toString());
        jdbc.update("UPDATE project_project SET opportunity_date=? WHERE id=UUID_TO_BIN(?)", "2026-07-01 16:00:00.000000", next.toString());
        jdbc.update("UPDATE project_project SET opportunity_date=? WHERE id=UUID_TO_BIN(?)", "2026-06-30 15:59:59.999999", previous.toString());
        jdbc.update("UPDATE project_project SET opportunity_date=NULL WHERE id=UUID_TO_BIN(?)", unknown.toString());
        var date = LocalDate.of(2026, 7, 1);
        var filter = new ProjectQuery(null, null, null, null, null, null, true,
                null, null, null, null, null, date, date);
        assertThat(projectService.countProjects(cityUserId, filter)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("opportunityDateStart", "2026-07-01").param("opportunityDateEnd", "2026-07-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString()).param("opportunityDateStart", "2026-07-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString()).param("opportunityDateEnd", "2026-07-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(3));
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("opportunityDateStart", "1000-01-01").param("opportunityDateEnd", "9999-12-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(4));
        mockMvc.perform(get("/api/v1/projects").header("X-NDI-Actor-Id", cityUserId.toString())
                        .param("opportunityDateStart", "2026-07-02").param("opportunityDateEnd", "2026-07-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PROJECT_INVALID_DATE_RANGE"));
        mockMvc.perform(post("/api/v1/projects/exports").header("X-NDI-Actor-Id", cityUserId.toString())
                        .contentType("application/json").content("{\"opportunityDateStart\":\"2026-07-02\",\"opportunityDateEnd\":\"2026-07-01\"}"))
                .andExpect(status().isBadRequest());
        var task = exportService.submit(cityUserId, filter);
        exportWorker.runPending();
        assertThat(exportService.find(cityUserId, task.id()).rowCount()).isEqualTo(2);
        var csv = ArgumentCaptor.forClass(byte[].class);
        verify(exportFileDirectory).storeCsv(eq(task.id()), csv.capture());
        assertThat(new String(csv.getValue(), StandardCharsets.UTF_8)).contains("原项目-2").doesNotContain("原项目-3", "原项目-4", "原项目-5");
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
        if (roleCode.equals("INTERFACE_REVIEWER")) jdbc.update("INSERT INTO identity_reviewer_province (reviewer_id, province_id) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?))", userId.toString(), provinceId.toString());
    }

    private void addUserInProvince(UUID userId, UUID userProvinceId, UUID userCityId, String roleCode) {
        jdbc.update("""
                INSERT INTO identity_user_account (id, login_name, display_name, province_id, city_id, enabled)
                VALUES (UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), UUID_TO_BIN(?), true)
                """, userId.toString(), userId.toString(), roleCode,
                userProvinceId == null ? null : userProvinceId.toString(),
                userCityId == null ? null : userCityId.toString());
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), ?)",
                userId.toString(), roleCode);
        if (roleCode.equals("INTERFACE_REVIEWER") && userProvinceId != null) jdbc.update("INSERT INTO identity_reviewer_province (reviewer_id, province_id) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?))", userId.toString(), userProvinceId.toString());
    }

    private byte[] uuidBytes(UUID value) {
        var buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }
}
