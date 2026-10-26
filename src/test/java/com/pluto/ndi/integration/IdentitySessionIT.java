package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.identity.service.IdentityBootstrapService;
import com.pluto.ndi.shared.security.SessionCookieSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(classes = NdiApplication.class, properties = {
        "ndi.auth.mode=SESSION", "ndi.auth.secure-cookie=false",
        "ndi.bootstrap.admin-login=bootstrap-admin", "ndi.bootstrap.admin-password=bootstrap-test-password",
        "ndi.projects.inactivity.enabled=false", "ndi.approvals.reminders-enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IdentitySessionIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("ndi").withUsername("ndi").withPassword("ndi_test_password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired IdentityBootstrapService bootstrap;
    UUID user;
    String login;
    final String password = "session-test-password";

    @BeforeEach void seed() {
        user = UUID.randomUUID(); login = "session-" + user;
        jdbc.update("INSERT INTO identity_user_account (id, login_name, display_name, enabled, password_hash) "
                + "VALUES (UUID_TO_BIN(?), ?, '认证测试用户', true, ?)", user.toString(), login, passwords.encode(password));
        jdbc.update("INSERT INTO identity_user_role (user_id, role_code) VALUES (UUID_TO_BIN(?), 'GROUP_REVIEWER')", user.toString());
    }

    @Test void enforcesSessionModeAndCsrfWithoutExposingSecrets() throws Exception {
        mvc.perform(get("/api/v1/auth/mode")).andExpect(jsonPath("$.data.mode").value("SESSION"));
        mvc.perform(get("/api/v1/auth/me").header("X-NDI-Actor-Id", user)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects/dev/actors")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(password))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        var session = login(password);
        assertThat(session.getValue()).hasSize(43);
        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.loginName").value(login)).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        String hash = jdbc.queryForObject("SELECT token_hash FROM identity_user_session WHERE user_id = UUID_TO_BIN(?)", String.class, user.toString());
        assertThat(hash).hasSize(64).isNotEqualTo(session.getValue());
        var result = mvc.perform(withCsrf(post("/api/v1/auth/sessions")).contentType(MediaType.APPLICATION_JSON)
                .content(credentials("wrong-password"))).andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(password, hash, session.getValue());
    }

    @Test void logsOutOnlyThePresentedSessionAndRejectsExpiredDisabledOrRevokedCredentials() throws Exception {
        var first = login(password); var second = login(password);
        mvc.perform(withCsrf(delete("/api/v1/auth/sessions/current")).cookie(first)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isOk());
        jdbc.update("UPDATE identity_user_account SET enabled=false WHERE id=UUID_TO_BIN(?)", user.toString());
        mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE identity_user_account SET enabled=true, credential_version=credential_version+1 WHERE id=UUID_TO_BIN(?)", user.toString());
        mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isUnauthorized());
        var third = login(password);
        jdbc.update("UPDATE identity_user_session SET expires_at='2000-01-01 00:00:00' WHERE user_id=UUID_TO_BIN(?)", user.toString());
        mvc.perform(get("/api/v1/auth/me").cookie(third)).andExpect(status().isUnauthorized());
    }

    @Test void refusesDisabledAndUninitializedAccountsWithTheSameGenericFailure() throws Exception {
        jdbc.update("UPDATE identity_user_account SET enabled=false WHERE id=UUID_TO_BIN(?)", user.toString());
        mvc.perform(withCsrf(post("/api/v1/auth/sessions")).contentType(MediaType.APPLICATION_JSON)
                .content(credentials(password))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_INVALID_CREDENTIALS"));
        jdbc.update("UPDATE identity_user_account SET enabled=true,password_hash=NULL WHERE id=UUID_TO_BIN(?)", user.toString());
        mvc.perform(withCsrf(post("/api/v1/auth/sessions")).contentType(MediaType.APPLICATION_JSON)
                .content(credentials(password))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_INVALID_CREDENTIALS"));
        mvc.perform(withCsrf(post("/api/v1/auth/sessions")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("loginName", "missing-" + user, "password", password))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("IDENTITY_INVALID_CREDENTIALS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identity_user_session WHERE user_id=UUID_TO_BIN(?)", Integer.class, user.toString())).isZero();
    }

    @Test void changesPasswordWithoutAcceptingWrongOrUnchangedPasswordAndRevokesEverySession() throws Exception {
        var first = login(password); var second = login(password);
        mvc.perform(withCsrf(post("/api/v1/auth/password")).cookie(first).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("currentPassword", "wrong", "newPassword", "new-test-password"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDENTITY_CURRENT_PASSWORD_INVALID"));
        mvc.perform(withCsrf(post("/api/v1/auth/password")).cookie(first).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("currentPassword", password, "newPassword", password))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDENTITY_PASSWORD_INVALID"));
        mvc.perform(withCsrf(post("/api/v1/auth/password")).cookie(first).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("currentPassword", password, "newPassword", "new-test-password"))))
                .andExpect(status().isOk()).andExpect(cookie().maxAge(SessionCookieSupport.COOKIE_NAME, 0));
        mvc.perform(get("/api/v1/auth/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isUnauthorized());
        login("new-test-password");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_event WHERE actor_id=UUID_TO_BIN(?) AND event_type='IDENTITY_PASSWORD_CHANGED'", Integer.class, user.toString())).isEqualTo(1);
    }

    @Test void updatesOnlyOwnProfileWithUniqueEmailAndLeavesAuthorizationUnchanged() throws Exception {
        var session = login(password);
        String email = user + "@example.test";
        mvc.perform(withCsrf(put("/api/v1/auth/me")).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("displayName", "修改后的姓名", "email", email.toUpperCase()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("修改后的姓名"))
                .andExpect(jsonPath("$.data.email").value(email)).andExpect(jsonPath("$.data.roleCodes[0]").value("GROUP_REVIEWER"));
        jdbc.update("INSERT INTO identity_user_account (id,login_name,display_name,enabled,email) VALUES (UUID_TO_BIN(?),?,'其他用户',true,?)",
                UUID.randomUUID().toString(), "other-" + user, "other-" + email);
        mvc.perform(withCsrf(put("/api/v1/auth/me")).cookie(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("displayName", "不应保存", "email", "other-" + email))))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(jsonPath("$.data.displayName").value("修改后的姓名"));
    }

    @Test void bootstrapsAdministratorIdempotentlyWithoutOverwritingPassword() throws Exception {
        String before = jdbc.queryForObject("SELECT password_hash FROM identity_user_account WHERE login_name='bootstrap-admin'", String.class);
        bootstrap.initialize();
        assertThat(jdbc.queryForObject("SELECT password_hash FROM identity_user_account WHERE login_name='bootstrap-admin'", String.class)).isEqualTo(before);
        assertThat(passwords.matches("bootstrap-test-password", before)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM system_audit_event WHERE event_type='IDENTITY_ADMIN_BOOTSTRAPPED'", Integer.class)).isEqualTo(1);
    }

    private String credentials(String value) throws Exception {
        return json.writeValueAsString(java.util.Map.of("loginName", login, "password", value));
    }
    private Cookie login(String value) throws Exception {
        var response = mvc.perform(withCsrf(post("/api/v1/auth/sessions")).contentType(MediaType.APPLICATION_JSON)
                .content(credentials(value))).andExpect(status().isCreated()).andReturn().getResponse();
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax");
        assertThat(response.getContentAsString()).doesNotContain("passwordHash", "token", "credentialVersion");
        return response.getCookie(SessionCookieSupport.COOKIE_NAME);
    }
    @Test void ordinaryAuthenticatedRequestsKeepCsrfStableAcrossParallelPageReads() throws Exception {
        var session=login(password);
        var issued=mvc.perform(get("/api/v1/auth/csrf").cookie(session)).andExpect(status().isOk()).andReturn().getResponse();
        var cookie=issued.getCookie("XSRF-TOKEN");
        var token=json.readTree(issued.getContentAsString()).path("data").path("token").asText();
        assertThat(cookie).isNotNull();
        for(int i=0;i<3;i++) {
            var read=mvc.perform(get("/api/v1/auth/me").cookie(session,cookie)).andExpect(status().isOk()).andReturn().getResponse();
            assertThat(read.getHeaders("Set-Cookie")).noneMatch(value->value.startsWith("XSRF-TOKEN="));
        }
        mvc.perform(put("/api/v1/auth/me").cookie(session,cookie).header("X-XSRF-TOKEN",token).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"稳定令牌测试\",\"email\":null}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/auth/me").cookie(session,cookie).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"无令牌\",\"email\":null}")).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder) throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        var token = json.readTree(response.getContentAsString()).path("data").path("token").asText();
        return builder.cookie(response.getCookie("XSRF-TOKEN")).header("X-XSRF-TOKEN", token);
    }
}
