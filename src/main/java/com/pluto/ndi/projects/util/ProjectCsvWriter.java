package com.pluto.ndi.projects.util;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import com.pluto.ndi.projects.service.ProjectSummary;

/** 固定列项目摘要 CSV；文本值在输出前防止表格公式执行。 */
public final class ProjectCsvWriter {

    private ProjectCsvWriter() {
    }

    public static byte[] write(List<ProjectSummary> projects) {
        var csv = new StringBuilder("\uFEFF项目编号,项目名称,客户名称,行业代码,项目总金额,当前阶段,网络化金额,格物金额,大数据金额,万悟金额,创建时间\r\n");
        for (var project : projects) {
            csv.append(row(
                    project.projectCode(), project.projectName(), project.customerName(),
                    project.industryCode(), project.totalAmount().toPlainString(), project.currentStageCode(),
                    amount(project, "NETWORK"), amount(project, "GEWU"),
                    amount(project, "BIG_DATA"), amount(project, "WANGWU"),
                    project.createdAt().toString()
            )).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String amount(ProjectSummary project, String code) {
        return project.categoryAmounts().stream()
                .filter(item -> code.equals(item.categoryCode()))
                .map(item -> item.expectedAmount().toPlainString())
                .findFirst().orElse("");
    }

    private static String row(String... values) {
        return java.util.Arrays.stream(values).map(ProjectCsvWriter::cell).collect(Collectors.joining(","));
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
