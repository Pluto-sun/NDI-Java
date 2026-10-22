package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.projects.service.*;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 使用标准省级目录和首次管理员配置，从空业务库开始，不执行演示 SQL。 */
@SpringBootTest(classes = NdiApplication.class, properties = {
    "ndi.auth.mode=SESSION", "ndi.auth.secure-cookie=false",
    "ndi.bootstrap.admin-login=zero-admin", "ndi.bootstrap.admin-password=zero-admin-password",
    "ndi.projects.inactivity.enabled=false", "ndi.approvals.reminders-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class ZeroDataProjectIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired IdentityAdministrationService admin;
    @Autowired IdentitySessionService sessions;
    @Autowired ProjectService projects;
    @Autowired ProjectProgressService progress;
    @Autowired ProjectOperationAuditService audit;
    @Autowired JdbcTemplate jdbc;

    @Test void initializesDirectoryAccountsAndProjectWithoutAnyLegacyOrDemoData() throws Exception {
        var administrator = sessions.login("zero-admin", "zero-admin-password").user().id();
        assertThat(admin.configuration(administrator).provinces()).hasSize(34);
        assertThat(admin.configuration(administrator).cities()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_project", Integer.class)).isZero();
        var province = admin.createProvince(administrator, " 零数据省 ");
        assertThat(province.name()).isEqualTo("零数据省");
        assertThatThrownBy(() -> admin.createProvince(administrator, "零数据省"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("同名省分");
        assertThatThrownBy(() -> admin.createProvince(administrator, " "))
                .isInstanceOf(BusinessException.class);
        var city = admin.createCity(administrator, province.id(), "零数据市");
        var leader = admin.create(administrator, new AdminAccountCommand("13800000001", "省分负责人", "",
                List.of("PROVINCE_REPORTER"), province.id(), null), "zero-user-password");
        var reporter = admin.create(administrator, new AdminAccountCommand("13800000002", "地市负责人", "",
                List.of("CITY_REPORTER"), province.id(), city.id()), "zero-user-password");
        assertThat(sessions.login("13800000002", "zero-user-password").user().id()).isEqualTo(reporter.id());
        assertThatThrownBy(() -> admin.createProvince(reporter.id(), "越权省"))
                .isInstanceOf(BusinessException.class);
        var project = projects.createProject(reporter.id(), new CreateProjectCommand(province.id(), city.id(),
                "从零建立的项目", "客户", "C25", "建设需求", new BigDecimal("100"), leader.id(), "客户经理",
                List.of(new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("50"))), "开始交流", "继续跟进"));
        progress.append(reporter.id(), project.id(), new AppendProgressCommand("新进展", "继续推进", null));
        var page = audit.find(reporter.id(), project.id(), PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(item -> item.eventType()).containsExactlyInAnyOrder("PROJECT_CREATED", "PROGRESS_APPENDED");
        assertThat(page.getContent()).extracting(item -> item.operatorName()).containsOnly("地市负责人");
        var first = audit.find(reporter.id(), project.id(), PageRequest.of(0, 1));
        var second = audit.find(reporter.id(), project.id(), PageRequest.of(1, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent().getFirst().id()).isNotEqualTo(second.getContent().getFirst().id());
        var otherProvince = admin.createProvince(administrator, "另省");
        var outsider = admin.create(administrator, new AdminAccountCommand("13800000003", "外省负责人", "",
                List.of("PROVINCE_REPORTER"), otherProvince.id(), null), "zero-user-password");
        assertThatThrownBy(() -> audit.find(outsider.id(), project.id(), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_event WHERE event_type='ORGANIZATION_PROVINCE_CREATED'", Integer.class)).isEqualTo(2);
        // 管理锁覆盖“不存在的省分”，并发首建也只能成功一次。
        try (var workers = Executors.newFixedThreadPool(2)) {
            var tasks = List.<java.util.concurrent.Callable<Boolean>>of(() -> createConcurrent(administrator), () -> createConcurrent(administrator));
            var results = workers.invokeAll(tasks);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
    }

    private boolean createConcurrent(UUID administrator) {
        try { admin.createProvince(administrator, "并发新省"); return true; }
        catch (BusinessException exception) {
            assertThat(exception.errorCode().code()).isEqualTo("ORGANIZATION_PROVINCE_CONFLICT");
            return false;
        }
    }
}
