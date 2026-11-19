package com.pluto.ndi.capabilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
import com.pluto.ndi.shared.imports.*;
import com.pluto.ndi.shared.imports.ImportModels.*;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import java.util.function.Predicate;
import java.util.*;

@Transactional
public class IntelligenceImport extends TabularImportEngine<IntelligenceService.Input> {
    private static final Pattern MONTH_TEXT =
            Pattern.compile("^([0-9]{4})(?:[-/.]([0-9]{1,2})|年([0-9]{1,2})月)$");
    private final IntelligenceService service;
    private final IntelligenceKind kind;

    public IntelligenceImport(
            IntelligenceService service,
            IntelligenceKind kind,
            NamedParameterJdbcTemplate jdbc,
            ImportWorkbookDirectory files,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit) {
        super(
                new ImportTaskStore(jdbc, json, kind.module()),
                files,
                json,
                clock,
                audit,
                kind.module());
        this.service = service;
        this.kind = kind;
    }

    protected void authorize(UUID actor) {
        service.scope(actor);
    }

    protected List<String> fields() {
        return kind.fields;
    }

    protected List<Rule> rules() {
        return fields().stream()
                .map(
                        field ->
                                new Rule(
                                        field,
                                        kind.required.contains(field) ? "REQUIRED" : "OPTIONAL",
                                        IntelligenceKind.MONTHS.contains(field)
                                                ? "填写完整年份和月份（YYYY-MM）"
                                                : ""))
                .toList();
    }

    protected List<String> missing(Map<String, String> mapping) {
        return ImportWorkflow.missing(mapping, kind.required.stream().toList());
    }

    protected IntelligenceService.Input validate(UUID actor, Map<String, String> cells) {
        return service.validate(actor, kind, service.excel(cells));
    }

    protected Object duplicateKey(IntelligenceService.Input value) {
        return CapabilityBusinessKey.intelligence(kind, value);
    }

    protected boolean duplicate(IntelligenceService.Input value) {
        return service.duplicate(kind, value, null);
    }

    protected UUID create(UUID actor, Map<String, String> cells) {
        return service.save(actor, kind, null, null, service.excel(cells)).id();
    }

    protected BusinessException invalid(String message) {
        return GewuDeploymentService.invalid(message);
    }

    protected BusinessException conflict() {
        return GewuDeploymentService.failure(HttpStatus.CONFLICT, "导入任务已变化");
    }

    @Override
    protected Predicate<String> sourceHeaders() {
        // 智能体主记录忽略横表里的 Token 列，截图同名列由月度导入按位置处理。
        return kind == IntelligenceKind.AGENTS_LIVE
                ? header -> !MonthlyTokenWorkbook.isMonthlyColumn(header)
                : super.sourceHeaders();
    }

    @Override
    protected List<ExcelWorkbook.Line> read(byte[] bytes, Configuration cfg)
            throws java.io.IOException {
        var dateFields = new HashSet<>(IntelligenceKind.MONTHS);
        dateFields.add("版本更新日期");
        return ExcelWorkbook.read(
                bytes,
                cfg.sheetName(),
                cfg.headerRowNo(),
                cfg.fieldMapping(),
                fields(),
                dateFields,
                sourceHeaders()).stream().map(line -> {
                    for (var field : IntelligenceKind.MONTHS) {
                        line.cells().computeIfPresent(field, (key, value) -> normalizeMonth(value));
                    }
                    return line;
                }).toList();
    }

    static String normalizeMonth(String value) {
        var matcher = MONTH_TEXT.matcher(value);
        if (matcher.matches()) {
            var month = Objects.requireNonNullElseGet(matcher.group(2), () -> matcher.group(3));
            return "%s-%02d".formatted(matcher.group(1), Integer.parseInt(month));
        }
        // Excel 日期单元格已经转成 ISO 日期，导入月份字段只保留年月。
        try {
            return YearMonth.from(LocalDate.parse(value)).toString();
        } catch (DateTimeParseException exception) {
            return value;
        }
    }
}
