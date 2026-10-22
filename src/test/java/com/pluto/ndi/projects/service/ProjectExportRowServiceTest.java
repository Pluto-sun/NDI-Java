package com.pluto.ndi.projects.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.*;
import com.pluto.ndi.projects.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

class ProjectExportRowServiceTest {
    private final ProjectRepository repository = mock(ProjectRepository.class);
    private final IdentityDirectory identity = mock(IdentityDirectory.class);
    private final OrganizationDirectory organizations = mock(OrganizationDirectory.class);
    private final com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository industries = mock(com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository.class);
    private final com.pluto.ndi.projects.mapper.ProjectInactivityMapper activities = mock(com.pluto.ndi.projects.mapper.ProjectInactivityMapper.class);
    private final ProjectExportRowService service = new ProjectExportRowService(repository, identity, organizations, industries, activities);

    @Test void summaryFieldsDoNotTriggerExtraDatabaseOrDirectoryLookups() {
        assertThat(service.rows(List.of(project()), ProjectExportField.select(null))).hasSize(1);
        verifyNoInteractions(repository, identity, organizations, industries, activities);
    }
    @Test void extraFieldsUseOneBatchPerDataSourceAndKeepExpertOrder() {
        var first = project(); var second = project();
        var province = UUID.randomUUID(); var city = UUID.randomUUID(); var leader = UUID.randomUUID();
        var ids = List.of(first.id(), second.id());
        var updatedAt = Instant.parse("2026-02-01T00:00:00Z");
        var lastActivity = Instant.parse("2026-03-01T00:00:00Z");
        when(repository.findExportDetails(ids)).thenReturn(ids.stream().map(id -> new ProjectExportDetails(id, province, city, leader, "DS_SIGN", "经理", updatedAt)).toList());
        when(repository.findExportExperts(ids)).thenReturn(List.of(new ProjectExportExpert(first.id(), "专家乙"), new ProjectExportExpert(first.id(), "专家甲")));
        when(organizations.findProvinceNames(Set.of(province))).thenReturn(Map.of(province, "省"));
        when(organizations.findCityNames(Set.of(city))).thenReturn(Map.of(city, "市"));
        when(identity.findDisplayNames(Set.of(leader))).thenReturn(Map.of(leader, "负责人"));
        var industry = mock(com.pluto.ndi.projects.domain.entity.ProjectIndustryReferenceEntity.class);
        when(industry.industryCode()).thenReturn("C25");
        when(industry.industryName()).thenReturn("行业名称");
        when(industries.findAllById(Set.of("C25"))).thenReturn(List.of(industry));
        when(activities.findLastActivities(ids)).thenReturn(Map.of(first.id(), lastActivity));
        var rows = service.rows(List.of(first, second), List.of(ProjectExportField.values()));
        assertThat(rows.getFirst().extra(ProjectExportField.SUPPORT_EXPERTS)).isEqualTo("专家乙、专家甲");
        assertThat(rows.getLast().extra(ProjectExportField.SUPPORT_EXPERTS)).isEmpty();
        assertThat(rows.getFirst().extra(ProjectExportField.PROVINCE_LEADER)).isEqualTo("负责人");
        assertThat(rows.getFirst().extra(ProjectExportField.CUSTOMER_INDUSTRY)).isEqualTo("行业名称");
        assertThat(rows.getFirst().extra(ProjectExportField.UPDATED_AT)).isEqualTo(lastActivity.toString());
        assertThat(rows.getLast().extra(ProjectExportField.UPDATED_AT)).isEqualTo(updatedAt.toString());
        verify(repository).findExportDetails(ids); verify(repository).findExportExperts(ids);
        verify(organizations).findProvinceNames(Set.of(province)); verify(organizations).findCityNames(Set.of(city));
        verify(identity).findDisplayNames(Set.of(leader));
        verify(industries).findAllById(Set.of("C25")); verify(activities).findLastActivities(ids);
        verifyNoMoreInteractions(repository, identity, organizations, industries, activities);
    }
    private ProjectSummary project() {
        return new ProjectSummary(UUID.randomUUID(), "NDI-1", "项目", "客户", "C25", BigDecimal.TEN,
                "SOLUTION_EXCHANGE", List.of(), Instant.parse("2026-01-01T00:00:00Z"));
    }
}
