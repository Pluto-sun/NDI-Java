package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import java.util.concurrent.*;
import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.*;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(classes = NdiApplication.class, properties = {
        "ndi.auth.mode=SESSION", "ndi.auth.secure-cookie=false",
        "ndi.bootstrap.admin-login=admin-test", "ndi.bootstrap.admin-password=admin-test-password",
        "ndi.projects.inactivity.enabled=false", "ndi.approvals.reminders-enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IdentityAdministrationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired IdentityAdministrationService admin;
    @Autowired IdentitySessionService sessions;
    @Autowired IdentityProfileService profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    UUID administrator;
    UUID province;
    UUID city;
    String phone;
    final String password = "account-test-password";

    @BeforeEach void seed() {
        jdbc.update("UPDATE identity_user_account SET enabled=false WHERE login_name <> 'admin-test'");
        jdbc.update("UPDATE identity_user_account SET enabled=true WHERE login_name='admin-test'");
        administrator = UUID.fromString(jdbc.queryForObject("SELECT BIN_TO_UUID(id) FROM identity_user_account WHERE login_name='admin-test'", String.class));
        province = UUID.randomUUID(); city = UUID.randomUUID();
        phone = "138" + String.format("%08d", ThreadLocalRandom.current().nextInt(100000000));
        jdbc.update("INSERT INTO organization_province (id,province_code,province_name,enabled) VALUES (UUID_TO_BIN(?),?,'管理测试省',true)", province.toString(), province.toString().replace("-", ""));
        jdbc.update("INSERT INTO organization_city (id,province_id,city_code,city_name,enabled) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,'管理测试市',true)", city.toString(), province.toString(), city.toString().replace("-", ""));
    }

    @Test void createsEditsSearchesAndAuditsWithoutLeakingCredentials() throws Exception {
        var created = admin.create(administrator, command(phone, "CITY_REPORTER", province, city), password);
        assertThat(created.passwordInitialized()).isTrue();
        assertThat(created.roleCodes()).containsExactly("CITY_REPORTER");
        var filtered = admin.list(administrator, phone, true, 0, 20);
        assertThat(filtered.getContent()).extracting(AdminAccountView::id).containsExactly(created.id());
        assertThat(admin.list(administrator, "%", null, 0, 20)).isEmpty();
        assertThat(sessions.login(phone, password).user().id()).isEqualTo(created.id());
        var updated = admin.update(administrator, created.id(), created.version(), new AdminAccountCommand(phone,
                " 修改后的负责人 ", " " + phone + "@EXAMPLE.TEST ", List.of("PROVINCE_REPORTER", "PROVINCE_DIGITAL_LEADER"), province, null));
        assertThat(updated.displayName()).isEqualTo("修改后的负责人");
        assertThat(updated.email()).isEqualTo(phone + "@example.test");
        assertThat(updated.roleCodes()).hasSize(2);
        assertThat(updated.version()).isGreaterThan(created.version());
        var cookie = loginCookie("admin-test", "admin-test-password");
        mvc.perform(get("/api/v1/admin/users").param("search", phone).cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(created.id().toString()))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_event WHERE resource_id=UUID_TO_BIN(?) AND event_type LIKE 'IDENTITY_ACCOUNT_%'", Integer.class, created.id().toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT CAST(detail AS CHAR) FROM system_audit_event WHERE resource_id=UUID_TO_BIN(?) AND event_type='IDENTITY_ACCOUNT_CREATED'", String.class, created.id().toString())).doesNotContain(password);
        var changes = json.readTree(jdbc.queryForObject("SELECT CAST(detail AS CHAR) FROM system_audit_event WHERE resource_id=UUID_TO_BIN(?) AND event_type='IDENTITY_ACCOUNT_UPDATED'", String.class, created.id().toString()));
        assertThat(changes.path("before").path("cityId").asText()).isEqualTo(city.toString());
        assertThat(changes.path("after").path("cityId").isNull()).isTrue();
    }

    @Test void rejectsDuplicateNamesEmailsInvalidPhonesRolesAndCrossProvinceScope() {
        var first = admin.create(administrator, new AdminAccountCommand(phone, "测试", phone + "@example.test", List.of("CITY_REPORTER"), province, city), password);
        assertCode(() -> admin.create(administrator, command(phone, "CITY_REPORTER", province, city), password), "IDENTITY_ACCOUNT_CONFLICT");
        assertCode(() -> admin.create(administrator, command("not-a-mobile", "SYSTEM_ADMIN", null, null), password), "IDENTITY_ACCOUNT_INVALID");
        assertCode(() -> admin.create(administrator, command("13912345678", "CITY_REPORTER", UUID.randomUUID(), city), password), "IDENTITY_ROLE_SCOPE_INVALID");
        assertCode(() -> admin.create(administrator, command("13912345678", "SYSTEM_ADMIN", province, null), password), "IDENTITY_ROLE_SCOPE_INVALID");
        assertCode(() -> admin.create(administrator, new AdminAccountCommand("13912345678", "测试", null,
                List.of("SYSTEM_ADMIN", "CITY_REPORTER"), province, city), password), "IDENTITY_ROLE_SCOPE_INVALID");
        var other = admin.create(administrator, command("13912345678", "GROUP_REVIEWER", null, null), password);
        assertCode(() -> admin.update(administrator, other.id(), other.version(), new AdminAccountCommand(other.loginName(), "不应保存", first.email(), List.of("GROUP_REVIEWER"), null, null)), "IDENTITY_ACCOUNT_CONFLICT");
        assertThat(admin.list(administrator, other.loginName(), null, 0, 20).getContent().getFirst().displayName()).isEqualTo("测试负责人");
    }

    @Test void requiresCurrentVersionAndDoesNotOverwriteConcurrentPersonalEdits() {
        var user = admin.create(administrator, command(phone, "CITY_REPORTER", province, city), password);
        profiles.update(user.id(), "本人已更新", null);
        assertCode(() -> admin.update(administrator, user.id(), user.version(), command(phone, "PROVINCE_REPORTER", province, null)), "IDENTITY_VERSION_CONFLICT");
        assertCode(() -> admin.resetPassword(administrator, user.id(), user.version(), "changed-password"), "IDENTITY_VERSION_CONFLICT");
        assertThat(profiles.current(user.id()).displayName()).isEqualTo("本人已更新");
    }

    @Test void revokesSessionsWhenDisabledResetOrScopeChangedAndNeverResurrectsOldSessions() {
        var user = admin.create(administrator, command(phone, "CITY_REPORTER", province, city), password);
        var first = sessions.login(phone, password).token();
        var disabled = admin.setEnabled(administrator, user.id(), user.version(), false);
        assertThat(sessions.resolveActor(first)).isEmpty();
        var enabled = admin.setEnabled(administrator, user.id(), disabled.version(), true);
        assertThat(sessions.resolveActor(first)).isEmpty();
        var second = sessions.login(phone, password).token();
        var reset = admin.resetPassword(administrator, user.id(), enabled.version(), "new-account-password");
        assertThat(sessions.resolveActor(second)).isEmpty();
        assertCode(() -> sessions.login(phone, password), "IDENTITY_INVALID_CREDENTIALS");
        var third = sessions.login(phone, "new-account-password").token();
        admin.update(administrator, user.id(), reset.version(), command(phone, "PROVINCE_REPORTER", province, null));
        assertThat(sessions.resolveActor(third)).isEmpty();
    }

    @Test void refusesAllNonAdministratorEndpointsAndRequiresCsrfAndVersion() throws Exception {
        var user = admin.create(administrator, command(phone, "GROUP_REVIEWER", null, null), password);
        var cookie = loginCookie(phone, password);
        mvc.perform(get("/api/v1/admin/users").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/configuration").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(csrf(post("/api/v1/admin/cities")).cookie(cookie).contentType("application/json")
                .content(json.writeValueAsString(Map.of("provinceId", province, "name", "越权地市")))).andExpect(status().isForbidden());
        mvc.perform(csrf(post("/api/v1/admin/users")).cookie(cookie).contentType("application/json")
                .content(json.writeValueAsString(Map.of("account", command("13988888888", "SYSTEM_ADMIN", null, null), "password", password)))).andExpect(status().isForbidden());
        mvc.perform(csrf(patch("/api/v1/admin/users/" + user.id() + "/enabled")).cookie(cookie).contentType("application/json")
                .content(json.writeValueAsString(Map.of("version", user.version(), "enabled", false)))).andExpect(status().isForbidden());
        mvc.perform(csrf(post("/api/v1/admin/users/" + user.id() + "/reset-password")).cookie(cookie).contentType("application/json")
                .content(json.writeValueAsString(Map.of("version", user.version(), "newPassword", password)))).andExpect(status().isForbidden());
        var adminCookie = loginCookie("admin-test", "admin-test-password");
        mvc.perform(patch("/api/v1/admin/users/" + user.id() + "/enabled").cookie(adminCookie).contentType("application/json")
                .content("{\"enabled\":false}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        mvc.perform(csrf(patch("/api/v1/admin/users/" + user.id() + "/enabled")).cookie(adminCookie).contentType("application/json")
                .content("{\"enabled\":false}")).andExpect(status().isBadRequest());
    }

    @Test void protectsSelfAndLastUsableAdministrator() {
        var self = admin.list(administrator, "admin-test", null, 0, 20).getContent().getFirst();
        assertCode(() -> admin.setEnabled(administrator, administrator, self.version(), false), "IDENTITY_SELF_ADMIN_PROTECTED");
        assertCode(() -> admin.update(administrator, administrator, self.version(), command("13966666666", "GROUP_REVIEWER", null, null)), "IDENTITY_SELF_ADMIN_PROTECTED");
        UUID passwordless = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account (id,login_name,display_name,enabled) VALUES (UUID_TO_BIN(?),?,'未开通管理员',true)", passwordless.toString(), "legacy-" + passwordless);
        jdbc.update("INSERT INTO identity_user_role (user_id,role_code) VALUES (UUID_TO_BIN(?),'SYSTEM_ADMIN')", passwordless.toString());
        assertCode(() -> admin.setEnabled(passwordless, administrator, self.version(), false), "IDENTITY_LAST_ADMIN_PROTECTED");
        assertThat(sessions.login("admin-test", "admin-test-password").user().id()).isEqualTo(administrator);
    }

    @Test void concurrentAdministratorsCannotDisableEachOtherAfterLosingTheirOwnPrivilege() throws Exception {
        var first = admin.create(administrator, command("13911111111", "SYSTEM_ADMIN", null, null), password);
        var second = admin.create(administrator, command("13922222222", "SYSTEM_ADMIN", null, null), password);
        jdbc.update("UPDATE identity_user_account SET enabled=false WHERE id=UUID_TO_BIN(?)", administrator.toString());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> outcome(start, first, second));
            var b = executor.submit(() -> outcome(start, second, first));
            start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK", "IDENTITY_ADMIN_REQUIRED");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_user_account u JOIN identity_user_role r ON u.id=r.user_id WHERE u.enabled=true AND u.password_hash IS NOT NULL AND r.role_code='SYSTEM_ADMIN'", Integer.class)).isEqualTo(1);
    }

    @Test void createsCityWithParentScopeAndSerializesConcurrentDuplicateNames() throws Exception {
        String name = "新增测试市-" + phone;
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> create = () -> { start.await(); try { admin.createCity(administrator, province, name); return "OK"; }
                catch (BusinessException exception) { return exception.errorCode().code(); } };
            var a = executor.submit(create); var b = executor.submit(create); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsOnly("ORGANIZATION_CATALOG_READ_ONLY");
        }
        assertThat(admin.configuration(administrator).cities()).noneMatch(item -> item.name().equals(name));
        assertCode(() -> admin.createCity(administrator, UUID.randomUUID(), "无效市"), "ORGANIZATION_CATALOG_READ_ONLY");
    }

    private String outcome(CountDownLatch start, AdminAccountView actor, AdminAccountView target) throws Exception {
        start.await();
        try { admin.setEnabled(actor.id(), target.id(), target.version(), false); return "OK"; }
        catch (BusinessException exception) { return exception.errorCode().code(); }
    }
    private AdminAccountCommand command(String login, String role, UUID provinceId, UUID cityId) {
        return new AdminAccountCommand(login, "测试负责人", null, List.of(role), provinceId, cityId);
    }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String code) {
        var thrown = catchThrowable(action);
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).errorCode().code()).isEqualTo(code);
    }
    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder builder) throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        return builder.cookie(response.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", json.readTree(response.getContentAsString()).path("data").path("token").asText());
    }
    private Cookie loginCookie(String loginName, String value) throws Exception {
        return mvc.perform(csrf(post("/api/v1/auth/sessions")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("loginName", loginName, "password", value))))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("ndi_session");
    }
}
