package com.pluto.ndi.capabilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.imports.*;
import com.pluto.ndi.shared.imports.ImportModels.Rule;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

@Service
@Transactional
public class GewuImportService extends TabularImportEngine<GewuDeploymentService.Input> {
    private final GewuDeploymentService deployments;

    public GewuImportService(
            GewuDeploymentService deployments,
            GewuImportStore store,
            ImportWorkbookDirectory files,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit) {
        super(store, files, json, clock, audit, "capability");
        this.deployments = deployments;
    }

    protected void authorize(UUID actor) {
        deployments.scope(actor);
    }

    protected List<String> fields() {
        return GewuDeploymentService.FIELDS;
    }

    protected List<Rule> rules() {
        return fields().stream()
                .map(
                        field ->
                                new Rule(
                                        field,
                                        GewuDeploymentService.REQUIRED.contains(field)
                                                ? "REQUIRED"
                                                : "OPTIONAL",
                                        ""))
                .toList();
    }

    protected List<String> missing(Map<String, String> mapping) {
        return ImportWorkflow.missing(mapping, GewuDeploymentService.REQUIRED);
    }

    protected GewuDeploymentService.Input validate(UUID actor, Map<String, String> cells) {
        return deployments.validate(actor, deployments.fromExcel(cells));
    }

    @Override
    protected List<com.pluto.ndi.shared.excel.ExcelWorkbook.Line> read(
            byte[] bytes, com.pluto.ndi.shared.imports.ImportModels.Configuration config)
            throws java.io.IOException {
        return com.pluto.ndi.shared.excel.ExcelWorkbook.read(
                bytes,
                config.sheetName(),
                config.headerRowNo(),
                config.fieldMapping(),
                fields(),
                Set.of("设备接入日期"));
    }

    protected Object duplicateKey(GewuDeploymentService.Input input) {
        return CapabilityBusinessKey.gewu(input);
    }

    protected boolean duplicate(GewuDeploymentService.Input input) {
        return deployments.duplicate(input, null);
    }

    protected UUID create(UUID actor, Map<String, String> cells) {
        return deployments.create(actor, deployments.fromExcel(cells)).id();
    }

    protected BusinessException invalid(String text) {
        return GewuDeploymentService.invalid(text);
    }

    protected BusinessException conflict() {
        return GewuDeploymentService.failure(HttpStatus.CONFLICT, "导入任务已变更，请刷新后重试");
    }
}
