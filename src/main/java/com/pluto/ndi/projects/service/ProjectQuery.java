package com.pluto.ndi.projects.service;

import java.util.Locale;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;

import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.ProjectCategory;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.filter.ListFilters;

/** 项目列表与导出共用的筛选条件。 */
public record ProjectQuery(
        String projectCode,
        String projectName,
        ProjectStage stage,
        String industryCode,
        UUID provinceId,
        UUID cityId,
        boolean includeTerminated,
        String signingSubjectCode,
        String provinceLeaderName,
        String cityAccountManagerName,
        String productSupportExpertName,
        String keyword,
        LocalDate opportunityDateStart,
        LocalDate opportunityDateEnd,
        String responsiblePersonName,
        List<UUID> provinceIds,
        List<UUID> cityIds,
        List<String> productCodes,
        List<ProjectStage> stages,
        List<String> industryCodes,
        List<String> signingSubjectCodes
) {

    public ProjectQuery {
        productCodes = productCodes == null ? List.of() : productCodes;
        if (productCodes.size() > 4 || productCodes.stream()
                .anyMatch(code -> ProjectCategory.fromCode(code).isEmpty())) {
            throw new BusinessException(ProjectErrorCode.INVALID_PRODUCT_FILTER);
        }
        productCodes = productCodes.stream().distinct().toList();
        provinceIds = locations(provinceId, provinceIds);
        cityIds = locations(cityId, cityIds);
        if (!cityIds.isEmpty() && (provinceIds.size() > 1
                || provinceIds.isEmpty() && (cityId == null || cityIds.size() > 1))) {
            throw new BusinessException(ProjectErrorCode.INVALID_ORGANIZATION);
        }
        projectCode = normalize(projectCode, true);
        projectName = normalize(projectName, false);
        industryCode = normalize(industryCode, true);
        signingSubjectCode = normalize(signingSubjectCode, true);
        stages = merge(stage, stages);
        industryCodes = merge(industryCode, ListFilters.values(industryCodes).stream()
                .map(value -> normalize(value, true)).toList());
        if (industryCodes.stream().anyMatch(value -> value.length() > 3))
            throw ListFilters.invalid();
        signingSubjectCodes = ListFilters.choices(
                merge(signingSubjectCode, ListFilters.values(signingSubjectCodes)),
                Set.of("PROVINCE_SIGN", "DS_SIGN"));
        provinceLeaderName = normalize(provinceLeaderName, false);
        cityAccountManagerName = normalize(cityAccountManagerName, false);
        productSupportExpertName = normalize(productSupportExpertName, false);
        keyword = normalize(keyword, false);
        responsiblePersonName=normalize(responsiblePersonName,false);
        if (opportunityDateStart != null && (opportunityDateStart.getYear() < 1000 || opportunityDateStart.getYear() > 9999)
                || opportunityDateEnd != null && (opportunityDateEnd.getYear() < 1000 || opportunityDateEnd.getYear() > 9999)
                || opportunityDateStart != null && opportunityDateEnd != null && opportunityDateStart.isAfter(opportunityDateEnd)) {
            throw new BusinessException(ProjectErrorCode.INVALID_DATE_RANGE);
        }
        if (signingSubjectCode != null && !Set.of("PROVINCE_SIGN", "DS_SIGN").contains(signingSubjectCode)) {
            throw new BusinessException(ProjectErrorCode.INVALID_SIGNING_SUBJECT);
        }
    }

    private static <T> List<T> merge(T legacy, List<T> values) {
        var result = new LinkedHashSet<T>();
        if (legacy != null) result.add(legacy);
        if (values != null) {
            if (values.size() > 500 || values.stream().anyMatch(java.util.Objects::isNull)) throw ListFilters.invalid();
            result.addAll(values);
        }
        return List.copyOf(result);
    }

    public ProjectQuery(String projectCode, String projectName, ProjectStage stage, String industryCode,
            UUID provinceId, UUID cityId, boolean includeTerminated, String signingSubjectCode,
            String provinceLeaderName, String cityAccountManagerName, String productSupportExpertName, String keyword,
            LocalDate opportunityDateStart, LocalDate opportunityDateEnd, String responsiblePersonName,
            List<UUID> provinceIds, List<UUID> cityIds, List<String> productCodes) {
        this(projectCode, projectName, stage, industryCode, provinceId, cityId, includeTerminated,
                signingSubjectCode, provinceLeaderName, cityAccountManagerName, productSupportExpertName,
                keyword, opportunityDateStart, opportunityDateEnd, responsiblePersonName,
                provinceIds, cityIds, productCodes, null, null, null);
    }

    public ProjectQuery(String projectCode, String projectName, ProjectStage stage, String industryCode,
            UUID provinceId, UUID cityId, boolean includeTerminated, String signingSubjectCode,
            String provinceLeaderName, String cityAccountManagerName, String productSupportExpertName, String keyword,
            LocalDate opportunityDateStart, LocalDate opportunityDateEnd, String responsiblePersonName) {
        this(projectCode, projectName, stage, industryCode, provinceId, cityId, includeTerminated, signingSubjectCode,
                provinceLeaderName, cityAccountManagerName, productSupportExpertName, keyword,
                opportunityDateStart, opportunityDateEnd, responsiblePersonName, null, null, null);
    }

    public ProjectQuery(String projectCode, String projectName, ProjectStage stage, String industryCode,
            UUID provinceId, UUID cityId, boolean includeTerminated, String signingSubjectCode,
            String provinceLeaderName, String cityAccountManagerName, String productSupportExpertName, String keyword,
            LocalDate opportunityDateStart, LocalDate opportunityDateEnd, String responsiblePersonName,
            List<UUID> provinceIds, List<UUID> cityIds) {
        this(projectCode, projectName, stage, industryCode, provinceId, cityId, includeTerminated, signingSubjectCode,
                provinceLeaderName, cityAccountManagerName, productSupportExpertName, keyword,
                opportunityDateStart, opportunityDateEnd, responsiblePersonName, provinceIds, cityIds, null);
    }

    private static List<UUID> locations(UUID single, List<UUID> multiple) {
        var result = new LinkedHashSet<UUID>();
        if (single != null) result.add(single);
        if (multiple != null) {
            if (multiple.size() > 500 || multiple.stream().anyMatch(java.util.Objects::isNull))
                throw new BusinessException(ProjectErrorCode.INVALID_ORGANIZATION);
            result.addAll(multiple);
        }
        return List.copyOf(result);
    }

    public ProjectQuery(String projectCode,String projectName,ProjectStage stage,String industryCode,
            UUID provinceId,UUID cityId,boolean includeTerminated,String signingSubjectCode,
            String provinceLeaderName,String cityAccountManagerName,String productSupportExpertName,String keyword,
            LocalDate opportunityDateStart,LocalDate opportunityDateEnd) {
        this(projectCode,projectName,stage,industryCode,provinceId,cityId,includeTerminated,signingSubjectCode,
            provinceLeaderName,cityAccountManagerName,productSupportExpertName,keyword,opportunityDateStart,opportunityDateEnd,null);
    }

    public ProjectQuery(String projectCode, String projectName, ProjectStage stage, String industryCode,
            UUID provinceId, UUID cityId, boolean includeTerminated, String signingSubjectCode,
            String provinceLeaderName, String cityAccountManagerName, String productSupportExpertName, String keyword) {
        this(projectCode, projectName, stage, industryCode, provinceId, cityId, includeTerminated,
                signingSubjectCode, provinceLeaderName, cityAccountManagerName, productSupportExpertName, keyword, null, null);
    }

    public ProjectQuery(String projectCode, String projectName, ProjectStage stage, String industryCode,
            UUID provinceId, UUID cityId, boolean includeTerminated) {
        this(projectCode, projectName, stage, industryCode, provinceId, cityId, includeTerminated,
                null, null, null, null, null);
    }

    public static ProjectQuery all() {
        return new ProjectQuery(null, null, null, null, null, null, true);
    }

    private static String normalize(String value, boolean uppercase) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var trimmed = value.trim();
        return uppercase ? trimmed.toUpperCase(Locale.ROOT) : trimmed;
    }
}
