package com.pluto.ndi.projects.service;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;

import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.shared.web.BusinessException;

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
        String responsiblePersonName
) {

    public ProjectQuery {
        projectCode = normalize(projectCode, true);
        projectName = normalize(projectName, false);
        industryCode = normalize(industryCode, true);
        signingSubjectCode = normalize(signingSubjectCode, true);
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
