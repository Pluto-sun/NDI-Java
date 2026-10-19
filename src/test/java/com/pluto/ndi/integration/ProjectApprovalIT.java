package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.ProjectInitiatorLevel;
import com.pluto.ndi.projects.domain.ApprovalStatus;
import com.pluto.ndi.projects.domain.MajorChangeType;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.repository.ProjectApprovalRequestRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.service.CreateProjectCommand;
import com.pluto.ndi.projects.service.MajorChangeCommand;
import com.pluto.ndi.projects.service.ProjectApprovalService;
import com.pluto.ndi.projects.service.ProjectService;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = NdiApplication.class)
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
    @Autowired private ProjectService projectService;
    @Autowired private ProjectApprovalService approvalService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectApprovalRequestRepository approvalRepository;

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
