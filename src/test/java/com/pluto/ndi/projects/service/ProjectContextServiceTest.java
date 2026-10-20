package com.pluto.ndi.projects.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectUser;
import com.pluto.ndi.organization.service.CityOption;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.organization.service.ProvinceOption;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.domain.entity.ProjectIndustryReferenceEntity;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.shared.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectContextServiceTest {

    private final UUID actorId = UUID.randomUUID();
    private final UUID provinceId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private IdentityDirectory identities;
    private OrganizationDirectory organizations;
    private ProjectIndustryReferenceRepository industries;
    private ProjectContextService service;

    @BeforeEach
    void setUp() {
        identities = mock(IdentityDirectory.class);
        organizations = mock(OrganizationDirectory.class);
        industries = mock(ProjectIndustryReferenceRepository.class);
        service = new ProjectContextService(identities, organizations, industries);
        when(identities.findProjectUser(actorId)).thenReturn(Optional.of(new ProjectUser(
                actorId, "省分负责人", provinceId, null, List.of("PROVINCE_REPORTER"))));
    }

    @Test
    void returnsReferenceDataFromModuleDirectories() {
        when(organizations.findEnabledProvinces()).thenReturn(List.of(new ProvinceOption(provinceId, "演示省")));
        when(organizations.findEnabledCities()).thenReturn(List.of(new CityOption(cityId, provinceId, "演示市")));
        var industry = mock(ProjectIndustryReferenceEntity.class);
        when(industry.industryCode()).thenReturn("C13");
        when(industry.industryName()).thenReturn("农副食品加工业");
        when(industries.findByEnabledTrueOrderByIndustryCodeAsc()).thenReturn(List.of(industry));

        var result = service.referenceData(actorId);

        assertThat(result.provinces()).hasSize(1);
        assertThat(result.cities()).hasSize(1);
        assertThat(result.industries()).containsExactly(new ProjectReferenceData.CodeLabel("C13", "农副食品加工业"));
        assertThat(result.categories()).hasSize(4);
        assertThat(result.stages()).hasSize(5);
    }

    @Test
    void restrictsLeaderDirectoryToOwnProvince() {
        var otherProvinceId = UUID.randomUUID();
        var error = catchThrowable(() -> service.provinceLeaders(actorId, otherProvinceId));

        assertThat(error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) error).errorCode()).isEqualTo(ProjectErrorCode.ACCESS_FORBIDDEN);
    }

    @Test
    void findsLeadersInOwnProvince() {
        var leader = new ProjectUser(UUID.randomUUID(), "负责人", provinceId, null, List.of("PROVINCE_REPORTER"));
        when(identities.findEligibleProvinceLeaders(provinceId)).thenReturn(List.of(leader));

        assertThat(service.provinceLeaders(actorId, provinceId)).containsExactly(leader);
        verify(identities).findEligibleProvinceLeaders(provinceId);
    }
}
