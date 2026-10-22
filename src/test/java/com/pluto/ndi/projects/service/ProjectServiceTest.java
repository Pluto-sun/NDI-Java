package com.pluto.ndi.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.organization.service.OrganizationLocation;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.repository.ProjectCategoryAmountRepository;
import com.pluto.ndi.projects.repository.ProjectChangeEventRepository;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.repository.ProjectProgressRepository;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectStageMilestoneRepository;
import com.pluto.ndi.projects.util.ProjectNumberGenerator;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {

    private final UUID actorId = UUID.randomUUID();
    private final UUID provinceId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID provinceLeaderId = UUID.randomUUID();

    private IdentityDirectory identityDirectory;
    private OrganizationDirectory organizationDirectory;
    private ProjectIndustryReferenceRepository industryReferenceRepository;
    private ProjectRepository projectRepository;
    private ProjectCategoryAmountRepository categoryAmountRepository;
    private ProjectStageMilestoneRepository stageMilestoneRepository;
    private ProjectProgressRepository progressRepository;
    private ProjectChangeEventRepository changeEventRepository;
    private ProjectDuplicateService duplicateService;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        identityDirectory = mock(IdentityDirectory.class);
        organizationDirectory = mock(OrganizationDirectory.class);
        industryReferenceRepository = mock(ProjectIndustryReferenceRepository.class);
        projectRepository = mock(ProjectRepository.class);
        categoryAmountRepository = mock(ProjectCategoryAmountRepository.class);
        stageMilestoneRepository = mock(ProjectStageMilestoneRepository.class);
        progressRepository = mock(ProjectProgressRepository.class);
        changeEventRepository = mock(ProjectChangeEventRepository.class);
        duplicateService = mock(ProjectDuplicateService.class);
        var clock = Clock.fixed(Instant.parse("2026-10-19T02:00:00Z"), ZoneOffset.UTC);
        service = new ProjectService(
                identityDirectory,
                organizationDirectory,
                industryReferenceRepository,
                projectRepository,
                categoryAmountRepository,
                stageMilestoneRepository,
                progressRepository,
                mock(com.pluto.ndi.projects.repository.ProjectDraftRepository.class),
                duplicateService,
                mock(ProjectCityWriteLock.class),
                changeEventRepository,
                new ProjectNumberGenerator(clock),
                new com.pluto.ndi.projects.mapper.ProjectMapper(),
                mock(ProjectAccessService.class),
                mock(ProjectMonthlySnapshotService.class),
                new ObjectMapper(),
                clock,
                mock(ProjectInactivityService.class)
        );
        when(identityDirectory.findProjectActor(actorId)).thenReturn(Optional.of(new ProjectActor(
                actorId, provinceId, cityId, true
        )));
        when(organizationDirectory.findEnabledLocation(provinceId, cityId))
                .thenReturn(Optional.of(new OrganizationLocation(provinceId, cityId)));
        when(identityDirectory.isEligibleProvinceLeader(provinceLeaderId, provinceId)).thenReturn(true);
        when(identityDirectory.findProjectUser(provinceLeaderId)).thenReturn(Optional.of(
                new com.pluto.ndi.identity.service.ProjectUser(provinceLeaderId, "省分负责人", provinceId, null, List.of("PROVINCE_REPORTER"))));
        when(identityDirectory.findDisplayNames(any())).thenReturn(java.util.Map.of(provinceLeaderId, "省分负责人"));
        when(identityDirectory.canCreateProject(any(), eq(provinceId), eq(cityId))).thenReturn(true);
        when(industryReferenceRepository.existsByIndustryCodeAndEnabledTrue("C25")).thenReturn(true);
        when(duplicateService.candidates(eq(cityId), any())).thenReturn(new ProjectDuplicateCheck("NONE", List.of()));
    }

    @Test
    void createsProjectWithInitialBusinessRecords() {
        var result = service.createProject(actorId, validCommand(new BigDecimal("100.00"), List.of(
                new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00")),
                new CreateProjectCommand.CategoryAmount("GEWU", new BigDecimal("40.00"))
        )));

        assertThat(result.projectCode()).startsWith("NDI-20261019-");
        assertThat(result.currentStageCode()).isEqualTo("SOLUTION_EXCHANGE");
        assertThat(result.importanceCode()).isEqualTo("NORMAL");
        assertThat(result.provinceId()).isEqualTo(provinceId);
        assertThat(result.cityId()).isEqualTo(cityId);
        assertThat(result.provinceLeaderName()).isEqualTo("省分负责人");
        assertThat(result.attributionMonth()).isEqualTo(java.time.LocalDate.of(2026, 10, 1));
        assertThat(result.categoryAmounts()).hasSize(2);
        verify(projectRepository).saveAndFlush(any());
        verify(categoryAmountRepository).saveAll(any());
        verify(stageMilestoneRepository).save(any());
        verify(progressRepository).save(any());
        verify(changeEventRepository).save(any());
    }

    @Test
    void normalizesCreationAttributesAndStoresExplicitProgressOwner() {
        var result = service.createProject(actorId, commandWithAttributes("  CUSTOMER_KEY  ", "  指定负责人  "));
        assertThat(result.importanceCode()).isEqualTo("CUSTOMER_KEY");
        var progress = org.mockito.ArgumentCaptor.forClass(
                com.pluto.ndi.projects.domain.entity.ProjectProgressEntity.class);
        verify(progressRepository).save(progress.capture());
        assertThat(progress.getValue().ownerName()).isEqualTo("指定负责人");
    }

    @Test
    void blankCreationAttributesUseNormalImportanceAndProvinceLeader() {
        var result = service.createProject(actorId, commandWithAttributes("  ", "  "));
        assertThat(result.importanceCode()).isEqualTo("NORMAL");
        var progress = org.mockito.ArgumentCaptor.forClass(
                com.pluto.ndi.projects.domain.entity.ProjectProgressEntity.class);
        verify(progressRepository).save(progress.capture());
        assertThat(progress.getValue().ownerName()).isEqualTo("省分负责人");
    }

    @Test
    void rejectsOversizedCreationAttributesBeforeWriting() {
        for (var command : List.of(commandWithAttributes("X".repeat(31), null),
                commandWithAttributes(null, "人".repeat(101)))) {
            var failure = org.assertj.core.api.Assertions.catchThrowable(() -> service.createProject(actorId, command));
            assertThat(failure).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.INVALID_CREATION_ATTRIBUTES);
        }
        org.mockito.Mockito.verify(projectRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    private CreateProjectCommand commandWithAttributes(String importance, String owner) {
        var base = validCommand(new BigDecimal("100.00"), List.of(
                new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))));
        return new CreateProjectCommand(base.provinceId(), base.cityId(), base.projectName(), base.customerName(),
                base.industryCode(), base.customerNeed(), base.totalAmount(), base.provinceLeaderUserId(),
                base.cityAccountManagerName(), base.categoryAmounts(), base.initialProgress(), base.nextAction(),
                null, false, null, null, List.of(), null, List.of(), null, importance, owner);
    }

    @Test
    void rejectsCategoryAmountsGreaterThanTotalAmount() {
        var exception = org.assertj.core.api.Assertions.catchThrowable(() -> service.createProject(
                actorId,
                validCommand(new BigDecimal("100.00"), List.of(
                        new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("80.00")),
                        new CreateProjectCommand.CategoryAmount("GEWU", new BigDecimal("30.00"))
                ))
        ));

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) exception).errorCode()).isEqualTo(ProjectErrorCode.INVALID_CATEGORY_AMOUNT);
    }

    @Test
    void rejectsExactDuplicateBeforeWritingProject() {
        when(duplicateService.candidates(eq(cityId), any())).thenReturn(new ProjectDuplicateCheck(
                "STRONG_DUPLICATE", List.of()));

        var failure = org.assertj.core.api.Assertions.catchThrowable(() -> service.createProject(
                actorId, validCommand(new BigDecimal("100.00"), List.of(
                        new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))))));

        assertThat(((BusinessException) failure).errorCode()).isEqualTo(ProjectErrorCode.DUPLICATE_PROJECT);
        org.mockito.Mockito.verify(projectRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void requiresReasonWhenSimilarProjectExists() {
        when(duplicateService.candidates(eq(cityId), any())).thenReturn(new ProjectDuplicateCheck(
                "DUPLICATE_CANDIDATE", List.of()));

        var failure = org.assertj.core.api.Assertions.catchThrowable(() -> service.createProject(
                actorId, validCommand(new BigDecimal("100.00"), List.of(
                        new CreateProjectCommand.CategoryAmount("NETWORK", new BigDecimal("60.00"))))));

        assertThat(((BusinessException) failure).errorCode())
                .isEqualTo(ProjectErrorCode.DUPLICATE_CONFIRMATION_REQUIRED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enrichesPagedSummariesWithOneDeduplicatedDirectoryLookup() {
        var scope = new com.pluto.ndi.identity.service.ProjectDataScope(
                com.pluto.ndi.identity.service.ProjectDataScope.Level.CITY, cityId);
        when(identityDirectory.findReadableProjectScope(any())).thenReturn(Optional.of(scope));
        var at = Instant.parse("2026-10-19T02:00:00Z");
        var first = com.pluto.ndi.projects.domain.entity.ProjectEntity.create(UUID.randomUUID(), "NDI-A", provinceId,
                cityId, "项目甲", "客户", "C25", "需求", BigDecimal.TEN, provinceLeaderId, "经理", "DS_SIGN", actorId, at);
        var second = com.pluto.ndi.projects.domain.entity.ProjectEntity.create(UUID.randomUUID(), "NDI-B", provinceId,
                cityId, "项目乙", "客户", "C25", "需求", BigDecimal.TEN, provinceLeaderId, "经理", "DS_SIGN", actorId, at);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        when(projectRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(first, second), pageable, 2));
        var page = service.findProjects(actorId, pageable);
        assertThat(page.getContent()).hasSize(2).allSatisfy(project -> {
            assertThat(project.provinceLeaderName()).isEqualTo("省分负责人");
            assertThat(project.signingSubjectCode()).isEqualTo("DS_SIGN");
            assertThat(project.cityId()).isEqualTo(cityId);
        });
        verify(identityDirectory).findDisplayNames(List.of(provinceLeaderId));
    }

    private CreateProjectCommand validCommand(
            BigDecimal totalAmount,
            List<CreateProjectCommand.CategoryAmount> categoryAmounts
    ) {
        return new CreateProjectCommand(
                provinceId,
                cityId,
                "智能制造项目",
                "客户单位",
                "C25",
                "需要建设智能产线",
                totalAmount,
                provinceLeaderId,
                "张三",
                categoryAmounts,
                "完成首次交流",
                "安排方案评审"
        );
    }
}
