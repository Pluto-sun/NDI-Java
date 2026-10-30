package com.pluto.ndi.revenue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.revenue.domain.*;
import com.pluto.ndi.revenue.mapper.RevenueImportMapper;
import com.pluto.ndi.revenue.util.*;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.imports.*;
import com.pluto.ndi.shared.imports.ImportModels.*;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

/** 计收只提供来源字段、唯一键和授权；任务编排复用公共导入引擎。 */
@Service
@Transactional
public class RevenueImportService extends TabularImportEngine<RevenueFields.Source> {
    private final RevenueAccessService access;
    private final RevenueService entries;

    public RevenueImportService(
            RevenueAccessService access,
            RevenueService entries,
            RevenueImportMapper tasks,
            ImportWorkbookDirectory files,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit) {
        super(tasks, files, json, clock, audit, "revenue");
        this.access = access;
        this.entries = entries;
    }

    protected void authorize(UUID actor) {
        access.create(actor);
    }

    protected List<String> fields() {
        return RevenueImport.fields();
    }

    protected List<Rule> rules() {
        return RevenueImport.rules();
    }

    protected List<String> missing(Map<String, String> mapping) {
        return ImportWorkflow.missing(mapping, RevenueImport.REQUIRED);
    }

    protected RevenueFields.Source validate(UUID actor, Map<String, String> values) {
        return entries.validate(actor, values);
    }

    protected Object duplicateKey(RevenueFields.Source s) {
        return List.of(s.month(), s.contractNo(), s.clauseNo());
    }

    protected boolean duplicate(RevenueFields.Source s) {
        return entries.duplicate(s);
    }

    protected UUID create(UUID actor, Map<String, String> values) {
        return entries.create(actor, values).id();
    }

    protected List<com.pluto.ndi.shared.excel.ExcelWorkbook.Line> read(
            byte[] bytes, Configuration config) throws java.io.IOException {
        return RevenueExcel.read(
                bytes, config.sheetName(), config.headerRowNo(), config.fieldMapping(), fields());
    }

    protected BusinessException invalid(String text) {
        return RevenueFields.invalid(text);
    }

    protected BusinessException conflict() {
        return new BusinessException(RevenueError.conflict());
    }
}
