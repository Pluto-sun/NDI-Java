package com.pluto.ndi.production.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.production.mapper.ProductionImportMapper;
import com.pluto.ndi.production.util.ProductionRules;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.imports.*;
import com.pluto.ndi.shared.imports.ImportModels.*;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

/** 排产保留业务转换，向导和任务持久化复用公共引擎。 */
@Service
@Transactional
public class ProductionImportService extends TabularImportEngine<ProductionPlan.Command> {
    private final ProductionAccessService access;
    private final ProductionPlanService plans;
    private final OrganizationDirectory organizations;

    public ProductionImportService(
            ProductionAccessService access,
            ProductionPlanService plans,
            ProductionImportMapper tasks,
            ImportWorkbookDirectory files,
            OrganizationDirectory organizations,
            ObjectMapper json,
            Clock clock,
            AuditWriter audit) {
        super(tasks, files, json, clock, audit, "production");
        this.access = access;
        this.plans = plans;
        this.organizations = organizations;
    }

    protected void authorize(UUID actor) {
        if (!access.require(actor).writable())
            throw new BusinessException(ProductionError.forbidden());
    }

    protected List<String> fields() {
        return ProductionImport.fields();
    }

    protected List<Rule> rules() {
        return ProductionImport.rules();
    }

    protected List<String> missing(Map<String, String> mapping) {
        var missing = new ArrayList<>(ImportWorkflow.missing(mapping, ProductionImport.REQUIRED));
        if (ProductionImport.AMOUNTS.stream()
                .noneMatch(f -> !mapping.getOrDefault(f, "").isBlank())) missing.add("至少一项排产金额");
        return missing;
    }

    protected ProductionPlan.Command validate(UUID actor, Map<String, String> cells) {
        return plans.validate(actor, command(cells));
    }

    protected Object duplicateKey(ProductionPlan.Command c) {
        return List.of(
                c.planMonth(),
                c.provinceId(),
                c.cityId(),
                ProductionRules.normalize(c.projectName()),
                ProductionRules.normalize(c.customerName()));
    }

    protected boolean duplicate(ProductionPlan.Command c) {
        return plans.duplicate(c);
    }

    protected UUID create(UUID actor, Map<String, String> cells) {
        return plans.create(actor, command(cells)).id();
    }

    protected BusinessException invalid(String text) {
        return new BusinessException(ProductionError.invalid(text));
    }

    protected BusinessException conflict() {
        return new BusinessException(ProductionError.conflict());
    }

    private ProductionPlan.Command command(Map<String, String> c) {
        var location =
                organizations
                        .findEnabledLocationByNames(c.get("省分"), c.get("地市"))
                        .orElseThrow(() -> invalid("省分或地市未在系统启用，或地市与省分不匹配"));
        var codes = new LinkedHashSet<String>();
        for (var name : c.getOrDefault("三化类别", "").split("[;；,，、]")) {
            var code =
                    switch (name.strip()) {
                        case "网络化", "工业专网", "NETWORK" -> "NETWORK";
                        case "格物", "数字化（格物）", "GEWU" -> "GEWU";
                        case "大数据", "工业大数据", "BIG_DATA" -> "BIG_DATA";
                        case "万悟", "智能化", "WANGWU" -> "WANGWU";
                        default -> "";
                    };
            if (code.isEmpty()) throw invalid("三化类别须为网络化、格物、大数据或万悟");
            codes.add(code);
        }
        String month =
                c.getOrDefault("排产月份", "")
                        .strip()
                        .replace("年", "-")
                        .replace("月", "")
                        .replace("/", "-");
        if (month.matches("[0-9]{4}-[0-9]"))
            month = month.substring(0, 5) + "0" + month.substring(5);
        if (month.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) month = month.substring(0, 7);
        return new ProductionPlan.Command(
                c.get("接口人"),
                month,
                location.provinceId(),
                location.cityId(),
                c.get("项目名称"),
                c.get("客户名称"),
                List.copyOf(codes),
                amount(c, 0),
                amount(c, 1),
                amount(c, 2),
                amount(c, 3),
                c.get("项目状态"));
    }

    private BigDecimal amount(Map<String, String> c, int index) {
        var value = c.getOrDefault(ProductionImport.AMOUNTS.get(index), "").strip();
        if (value.isEmpty()) return null;
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw invalid(ProductionImport.AMOUNTS.get(index) + "须为数字");
        }
    }
}
