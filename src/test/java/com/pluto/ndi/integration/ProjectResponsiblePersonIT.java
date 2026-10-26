package com.pluto.ndi.integration;

import static org.assertj.core.api.Assertions.*;

import com.pluto.ndi.NdiApplication;
import com.pluto.ndi.projects.domain.*;
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

import java.math.BigDecimal;
import java.util.*;

@SpringBootTest(
        classes = NdiApplication.class,
        properties = {
            "ndi.projects.inactivity.enabled=false",
            "ndi.projects.export.initial-delay-ms=600000"
        })
@Testcontainers(disabledWithoutDocker = true)
class ProjectResponsiblePersonIT {
    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("ndi")
                    .withUsername("ndi")
                    .withPassword("ndi_test_password");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired ProjectService projects;
    @Autowired ProjectPeopleService people;
    @Autowired ProjectProfileService profiles;
    @Autowired ProjectInactivityService inactivity;
    @Autowired JdbcTemplate jdbc;

    private record Fixture(
            UUID province,
            UUID city,
            UUID reporter,
            UUID leaderA,
            UUID leaderB,
            UUID reviewer,
            UUID admin) {}

    private Fixture fixture() {
        UUID province = UUID.randomUUID(), city = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO organization_province VALUES(UUID_TO_BIN(?),?,?,TRUE)",
                province.toString(),
                province.toString().replace("-", ""),
                "人员省" + province.toString().substring(0, 8));
        jdbc.update(
                "INSERT INTO organization_city VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,TRUE)",
                city.toString(),
                province.toString(),
                city.toString().replace("-", ""),
                "人员市" + city.toString().substring(0, 8));
        UUID reporter = user(province, city, "c-", "CITY_REPORTER"),
                a = user(province, null, "a-", "PROVINCE_REPORTER"),
                b = user(province, null, "b-", "PROVINCE_REPORTER");
        UUID reviewer = user(null, null, "r-", "INTERFACE_REVIEWER"),
                admin = user(null, null, "z-", "SYSTEM_ADMIN");
        jdbc.update(
                "INSERT INTO identity_reviewer_province VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",
                reviewer.toString(),
                province.toString());
        return new Fixture(province, city, reporter, a, b, reviewer, admin);
    }

    private UUID user(UUID province, UUID city, String prefix, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " identity_user_account(id,login_name,display_name,province_id,city_id,enabled)"
                    + " VALUES(UUID_TO_BIN(?),?,?,UUID_TO_BIN(?),UUID_TO_BIN(?),TRUE)",
                id.toString(),
                prefix + id,
                "人员账号" + prefix,
                province == null ? null : province.toString(),
                city == null ? null : city.toString());
        jdbc.update("INSERT INTO identity_user_role VALUES(UUID_TO_BIN(?),?)", id.toString(), role);
        return id;
    }

    private ProjectSummary create(Fixture f) {
        return projects.createProject(
                f.reporter(),
                new CreateProjectCommand(
                        f.province(),
                        f.city(),
                        "人员项目" + UUID.randomUUID(),
                        "客户",
                        "C25",
                        "需求",
                        BigDecimal.TEN,
                        null,
                        null,
                        List.of(new CreateProjectCommand.CategoryAmount("NETWORK", BigDecimal.ONE)),
                        "首次进展",
                        "继续跟进",
                        null,
                        false,
                        null,
                        "PROVINCE_SIGN",
                        List.of(),
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        List.of("张三", "李四")));
    }

    private ProjectResponsiblePerson person(
            ProjectResponsiblePerson original, String role, UUID account) {
        return new ProjectResponsiblePerson(
                original.id(), original.name(), role, account, null, false);
    }

    @Test
    void createsUnassignedPeopleAndValidatesRolesBindingsScopeAndVersion() {
        var f = fixture();
        var created = create(f);
        var detail = projects.findDetail(f.reporter(), created.id());
        assertThat(detail.provinceLeaderUserId()).isNull();
        assertThat(detail.responsiblePersons()).hasSize(2);
        assertThat(detail.responsiblePersons())
                .allMatch(p -> p.responsibility() == null && p.userId() == null);
        var a = detail.responsiblePersons().get(0);
        var b = detail.responsiblePersons().get(1);
        assertThatThrownBy(
                        () ->
                                people.configure(
                                        f.reporter(), created.id(), detail.versionNo(), List.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                people.configure(
                                        f.reporter(),
                                        created.id(),
                                        detail.versionNo(),
                                        List.of(
                                                person(a, "PROVINCE_LEADER", f.leaderA()),
                                                person(b, "PROVINCE_LEADER", f.leaderB()))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                people.configure(
                                        f.reporter(),
                                        created.id(),
                                        detail.versionNo(),
                                        List.of(person(a, "PROVINCE_LEADER", f.reporter()), b)))
                .isInstanceOf(BusinessException.class);
        var foreign = fixture();
        assertThatThrownBy(
                        () ->
                                people.configure(
                                        f.reporter(),
                                        created.id(),
                                        detail.versionNo(),
                                        List.of(
                                                person(a, "PROVINCE_LEADER", foreign.leaderA()),
                                                b)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                people.configure(
                                        foreign.reporter(),
                                        created.id(),
                                        detail.versionNo(),
                                        detail.responsiblePersons()))
                .isInstanceOf(BusinessException.class);
        var configured =
                people.configure(
                        f.reporter(),
                        created.id(),
                        detail.versionNo(),
                        List.of(
                                person(a, "PROVINCE_LEADER", f.leaderB()),
                                person(b, "CITY_MANAGER", null)));
        assertThat(configured.versionNo()).isGreaterThan(detail.versionNo());
        assertThat(configured.provinceLeaderUserId()).isEqualTo(f.leaderB());
        assertThatThrownBy(
                        () ->
                                people.configure(
                                        f.reporter(),
                                        created.id(),
                                        detail.versionNo(),
                                        configured.responsiblePersons()))
                .isInstanceOf(BusinessException.class);
        assertThat(people.accountOptions(f.reporter(), created.id(), "", true))
                .extracting(u -> u.id())
                .containsExactlyInAnyOrder(f.leaderA(), f.leaderB());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM identity_user_role WHERE"
                                    + " user_id=UUID_TO_BIN(?)",
                                Integer.class,
                                f.reporter().toString()))
                .isEqualTo(1);
        var multiple =
                people.configure(
                        f.reporter(),
                        created.id(),
                        configured.versionNo(),
                        List.of(person(a, "CITY_MANAGER", null), person(b, "CITY_MANAGER", null)));
        assertThat(multiple.responsiblePersons())
                .allMatch(p -> "CITY_MANAGER".equals(p.responsibility()));
        var query =
                new ProjectQuery(
                        null, null, null, null, null, null, true, null, null, null, null, null,
                        null, null, "李四");
        assertThat(projects.findProjects(f.reporter(), query, PageRequest.of(0, 20)).getContent())
                .extracting(ProjectSummary::id)
                .contains(created.id());
        profiles.update(
                f.reporter(),
                created.id(),
                new UpdateProjectProfileCommand(
                        multiple.versionNo(), "客户新名", "C25", "新需求", null, null, null));
        assertThat(projects.findDetail(f.reporter(), created.id()).responsiblePersons()).hasSize(2);
        jdbc.update(
                "UPDATE project_project SET current_stage_code='TERMINATED' WHERE"
                    + " id=UUID_TO_BIN(?)",
                created.id().toString());
        assertThatThrownBy(
                        () ->
                                people.configure(
                                        f.reporter(),
                                        created.id(),
                                        multiple.versionNo(),
                                        multiple.responsiblePersons()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reminderSelectsBoundAccountThenFirstCandidateAndImmediatelyRoutesToReviewer() {
        var f = fixture();
        var created = create(f);
        var detail = projects.findDetail(f.reporter(), created.id());
        var original = detail.responsiblePersons();
        var bound =
                people.configure(
                        f.reporter(),
                        created.id(),
                        detail.versionNo(),
                        List.of(
                                person(original.get(0), "PROVINCE_LEADER", f.leaderB()),
                                original.get(1)));
        var direct = inactivity.trigger(f.admin(), created.id(), 30);
        assertThat(direct.recipients()).isEqualTo(1);
        assertRecipient(direct.caseId(), f.leaderB());
        var free =
                people.configure(
                        f.reporter(),
                        created.id(),
                        bound.versionNo(),
                        List.of(person(original.get(0), null, null), original.get(1)));
        var fallback = inactivity.trigger(f.admin(), created.id(), 30);
        assertThat(fallback.recipients()).isEqualTo(1);
        assertRecipient(fallback.caseId(), f.leaderA());
        assertThat(inactivity.trigger(f.admin(), created.id(), 30).delivered()).isFalse();
        people.configure(
                f.reporter(),
                created.id(),
                free.versionNo(),
                List.of(person(original.get(0), "PROVINCE_LEADER", f.leaderB()), original.get(1)));
        jdbc.update(
                "UPDATE identity_user_account SET enabled=FALSE WHERE id"
                    + " IN(UUID_TO_BIN(?),UUID_TO_BIN(?))",
                f.leaderA().toString(),
                f.leaderB().toString());
        var redirected = inactivity.trigger(f.admin(), created.id(), 30);
        assertThat(redirected.audience()).isEqualTo("INTERFACE");
        assertThat(redirected.recipients()).isEqualTo(1);
        assertRecipient(redirected.caseId(), f.reviewer());
        assertThat(inactivity.trigger(f.admin(), created.id(), 33).delivered()).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM project_inactivity_notice WHERE"
                                    + " case_id=UUID_TO_BIN(?)",
                                Integer.class,
                                redirected.caseId().toString()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM system_audit_event WHERE"
                                    + " resource_id=UUID_TO_BIN(?) AND detail LIKE '%立即转交%'",
                                Integer.class, created.id().toString()))
                .isGreaterThan(0);
    }

    private void assertRecipient(UUID caseId, UUID user) {
        assertThat(
                        jdbc.queryForList(
                                "SELECT BIN_TO_UUID(recipient_id) FROM project_inactivity_notice"
                                    + " WHERE case_id=UUID_TO_BIN(?)",
                                String.class,
                                caseId.toString()))
                .containsExactly(user.toString());
    }
}
