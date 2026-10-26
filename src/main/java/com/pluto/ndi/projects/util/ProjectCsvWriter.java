package com.pluto.ndi.projects.util;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.ZoneId;
import java.util.stream.Collectors;

import com.pluto.ndi.projects.service.ProjectSummary;
import com.pluto.ndi.projects.service.ProjectExportRow;
import com.pluto.ndi.projects.domain.ProjectExportField;
import com.pluto.ndi.projects.domain.ProjectStage;
import com.pluto.ndi.projects.domain.ProjectCategory;

/** 按白名单字段及指定顺序输出 CSV，所有文本统一防止表格公式执行。 */
public final class ProjectCsvWriter {

    private ProjectCsvWriter() {
    }

    public static byte[] write(List<ProjectSummary> projects) {
        return writeRows(projects.stream().map(ProjectExportRow::summary).toList(), ProjectExportField.select(null));
    }

    public static byte[] writeRows(List<ProjectExportRow> projects, List<ProjectExportField> fields) {
        var csv = new StringBuilder("\uFEFF").append(fields.stream().map(ProjectExportField::label)
                .collect(Collectors.joining(","))).append("\r\n");
        for (var project : projects) {
            csv.append(fields.stream().map(field -> cell(value(project, field))).collect(Collectors.joining(","))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String value(ProjectExportRow row, ProjectExportField field) {
        var project = row.project();
        return switch (field) {
            case PROJECT_CODE -> project.projectCode();
            case PROJECT_NAME -> project.projectName();
            case CUSTOMER_NAME -> project.customerName();
            case INDUSTRY_CODE -> project.industryCode();
            case TOTAL_AMOUNT -> project.totalAmount().toPlainString();
            case CURRENT_STAGE_CODE -> project.currentStageCode();
            case CURRENT_STAGE_NAME -> ProjectStage.valueOf(project.currentStageCode()).label();
            case NETWORK_AMOUNT -> amount(project, "NETWORK");
            case GEWU_AMOUNT -> amount(project, "GEWU");
            case BIG_DATA_AMOUNT -> amount(project, "BIG_DATA");
            case WANGWU_AMOUNT -> amount(project, "WANGWU");
            case CREATED_AT -> project.createdAt().toString();
            case RESPONSIBLE_PERSONS -> project.responsiblePersons().stream().map(com.pluto.ndi.projects.domain.ProjectResponsiblePerson::name).collect(Collectors.joining("；"));
            case PROVINCE, CITY, SIGNING_SUBJECT, PROVINCE_LEADER, CITY_MANAGER, SUPPORT_EXPERTS, CUSTOMER_INDUSTRY, UPDATED_AT -> row.extra(field);
            case CATEGORIES -> project.categoryAmounts().stream().map(item -> ProjectCategory.valueOf(item.categoryCode()))
                    .sorted().map(ProjectCategory::label).collect(Collectors.joining("、"));
            case OPPORTUNITY_DATE -> project.opportunityDate() == null ? "" : project.opportunityDate()
                    .atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString();
        };
    }

    private static String amount(ProjectSummary project, String code) {
        return project.categoryAmounts().stream()
                .filter(item -> code.equals(item.categoryCode()))
                .map(item -> item.expectedAmount().toPlainString())
                .findFirst().orElse("");
    }

    private static String cell(String value) {
        var safe = value == null ? "" : value;
        var trimmed = safe.stripLeading();
        if (!trimmed.isEmpty() && (trimmed.charAt(0) == '=' || trimmed.charAt(0) == '+'
                || trimmed.charAt(0) == '-' || trimmed.charAt(0) == '@')
                || !safe.isEmpty() && (safe.charAt(0) == '\t' || safe.charAt(0) == '\r')) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
