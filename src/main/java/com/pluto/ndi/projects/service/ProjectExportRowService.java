package com.pluto.ndi.projects.service;

import java.util.List;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.projects.domain.ProjectExportField;
import com.pluto.ndi.projects.domain.ProjectExportDetails;
import com.pluto.ndi.projects.domain.ProjectExportExpert;
import com.pluto.ndi.projects.repository.ProjectRepository;
import com.pluto.ndi.projects.repository.ProjectIndustryReferenceRepository;
import com.pluto.ndi.projects.domain.entity.ProjectIndustryReferenceEntity;
import com.pluto.ndi.projects.mapper.ProjectInactivityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 为已授权的项目摘要批量补齐所选列，避免逐项目加载详情或关联集合。 */
@Service
public class ProjectExportRowService {
    private static final Set<ProjectExportField> DETAILS = Set.of(ProjectExportField.PROVINCE, ProjectExportField.CITY,
            ProjectExportField.SIGNING_SUBJECT, ProjectExportField.PROVINCE_LEADER, ProjectExportField.CITY_MANAGER,
            ProjectExportField.SUPPORT_EXPERTS, ProjectExportField.UPDATED_AT);
    private final ProjectRepository projects;
    private final IdentityDirectory identity;
    private final OrganizationDirectory organizations;
    private final ProjectIndustryReferenceRepository industries;
    private final ProjectInactivityMapper activities;

    public ProjectExportRowService(ProjectRepository projects, IdentityDirectory identity, OrganizationDirectory organizations,
            ProjectIndustryReferenceRepository industries, ProjectInactivityMapper activities) {
        this.projects = projects;
        this.identity = identity;
        this.organizations = organizations;
        this.industries = industries;
        this.activities = activities;
    }

    @Transactional(readOnly = true)
    public List<ProjectExportRow> rows(List<ProjectSummary> summaries, List<ProjectExportField> fields) {
        boolean needsDetails = fields.stream().anyMatch(DETAILS::contains);
        if (summaries.isEmpty() || !needsDetails && !fields.contains(ProjectExportField.CUSTOMER_INDUSTRY)) {
            return summaries.stream().map(ProjectExportRow::summary).toList();
        }
        var ids = summaries.stream().map(ProjectSummary::id).toList();
        var details = needsDetails ? projects.findExportDetails(ids).stream()
                .collect(Collectors.toMap(ProjectExportDetails::id, item -> item)) : Map.<UUID, ProjectExportDetails>of();
        var provinces = fields.contains(ProjectExportField.PROVINCE) ? organizations.findProvinceNames(details.values().stream()
                .map(ProjectExportDetails::provinceId).collect(Collectors.toSet())) : Map.<UUID, String>of();
        var cities = fields.contains(ProjectExportField.CITY) ? organizations.findCityNames(details.values().stream()
                .map(ProjectExportDetails::cityId).collect(Collectors.toSet())) : Map.<UUID, String>of();
        var leaders = fields.contains(ProjectExportField.PROVINCE_LEADER) ? identity.findDisplayNames(details.values().stream()
                .map(ProjectExportDetails::provinceLeaderUserId).collect(Collectors.toSet())) : Map.<UUID, String>of();
        var experts = fields.contains(ProjectExportField.SUPPORT_EXPERTS) ? projects.findExportExperts(ids).stream().collect(
                Collectors.groupingBy(ProjectExportExpert::projectId, Collectors.mapping(ProjectExportExpert::name, Collectors.toList()))) : Map.<UUID, List<String>>of();
        var industryNames = fields.contains(ProjectExportField.CUSTOMER_INDUSTRY) ? industries.findAllById(summaries.stream()
                .map(ProjectSummary::industryCode).collect(Collectors.toSet())).stream().collect(Collectors.toMap(
                ProjectIndustryReferenceEntity::industryCode, ProjectIndustryReferenceEntity::industryName)) : Map.<String, String>of();
        var lastActivities = fields.contains(ProjectExportField.UPDATED_AT) ? activities.findLastActivities(ids) : Map.<UUID, Instant>of();
        return summaries.stream().map(summary -> {
            var detail = details.get(summary.id());
            if (needsDetails && detail == null) throw new IllegalStateException("导出项目元数据已变化，请重新提交");
            var values = new EnumMap<ProjectExportField, String>(ProjectExportField.class);
            values.put(ProjectExportField.CUSTOMER_INDUSTRY, industryNames.getOrDefault(summary.industryCode(), ""));
            if (detail != null) {
                values.put(ProjectExportField.PROVINCE, provinces.getOrDefault(detail.provinceId(), ""));
                values.put(ProjectExportField.CITY, cities.getOrDefault(detail.cityId(), ""));
                values.put(ProjectExportField.SIGNING_SUBJECT, detail.signingSubjectCode());
                values.put(ProjectExportField.PROVINCE_LEADER, detail.provinceLeaderUserId()==null ? "" : leaders.getOrDefault(detail.provinceLeaderUserId(), ""));
                values.put(ProjectExportField.CITY_MANAGER, detail.cityAccountManagerName());
                values.put(ProjectExportField.SUPPORT_EXPERTS, String.join("、", experts.getOrDefault(summary.id(), List.of())));
                values.put(ProjectExportField.UPDATED_AT, lastActivities.getOrDefault(summary.id(), detail.updatedAt()).toString());
            }
            return new ProjectExportRow(summary, values);
        }).toList();
    }
}
