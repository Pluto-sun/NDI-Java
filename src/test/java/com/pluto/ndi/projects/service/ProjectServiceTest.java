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
                clock
        );
        when(identityDirectory.findProjectActor(actorId)).thenReturn(Optional.of(new ProjectActor(
                actorId, provinceId, cityId, true
        )));
        when(organizationDirectory.findEnabledLocation(provinceId, cityId))
                .thenReturn(Optional.of(new OrganizationLocation(provinceId, cityId)));
        when(identityDirectory.isEligibleProvinceLeader(provinceLeaderId, provinceId)).thenReturn(true);
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
        assertThat(result.categoryAmounts()).hasSize(2);
        verify(projectRepository).saveAndFlush(any());
        verify(categoryAmountRepository).saveAll(any());
        verify(stageMilestoneRepository).save(any());
        verify(progressRepository).save(any());
        verify(changeEventRepository).save(any());
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
