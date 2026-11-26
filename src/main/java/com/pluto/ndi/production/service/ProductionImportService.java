package com.pluto.ndi.production.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.ImportWorkbookDirectory;
import com.pluto.ndi.organization.service.OrganizationDirectory;
import com.pluto.ndi.organization.service.OrganizationLocation;
import com.pluto.ndi.production.domain.*;
import com.pluto.ndi.production.mapper.ProductionImportMapper;
import com.pluto.ndi.production.util.ProductionRules;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.excel.ExcelWorkbook;
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

    @Override
    public Task current(UUID actor) {
        return currentFields(super.current(actor));
    }

    @Override
    public Task save(UUID actor, UUID id, long version, int step, Configuration config, List<Integer> selected) {
        return currentFields(super.save(actor, id, version, step, withoutLegacyCategory(config), selected));
    }

    private Task currentFields(Task task) {
        if (task == null || task.inspection().fields().equals(fields())) return task;
        var inspection = new ExcelWorkbook.Inspection(
                task.inspection().fileName(), fields(), task.inspection().sheets());
        return new Task(task.id(), task.version(), task.step(), task.fileName(), inspection,
                withoutLegacyCategory(task.configuration()), task.selectedRows(), task.batch(), task.updatedAt(), rules());
    }

    private Configuration withoutLegacyCategory(Configuration config) {
        if (config == null || config.fieldMapping() == null || !config.fieldMapping().containsKey("三化类别"))
            return config;
        var mapping = new LinkedHashMap<>(config.fieldMapping());
        mapping.remove("三化类别");
        return new Configuration(config.sheetName(), config.headerRowNo(), mapping);
    }

    @Override
    protected List<ExcelWorkbook.Line> read(byte[] bytes, Configuration config)
            throws java.io.IOException {
        return ExcelWorkbook.read(bytes, config.sheetName(), config.headerRowNo(),
                config.fieldMapping(), fields(), Set.of(), sourceHeaders(), Set.copyOf(ProductionImport.AMOUNTS));
    }

    @Override
    protected boolean canReviseField(Configuration config, String field) {
        return super.canReviseField(config, field)
                && (!ProductionImport.AMOUNTS.contains(field)
                        || !config.fieldMapping().getOrDefault(field, "").isBlank());
    }

    protected ProductionPlan.Command validate(UUID actor, Map<String, String> cells) {
        return plans.validate(actor, command(cells));
    }

    @Override
    protected java.util.function.Function<Map<String, String>, ProductionPlan.Command> batchValidator(UUID actor) {
        var scope = access.require(actor);
        var locations = new HashMap<List<String>, Optional<OrganizationLocation>>();
        return cells -> {
            var key = Arrays.asList(cells.get("省分"), cells.get("地市"));
            var location = locations.computeIfAbsent(key,
                    names -> organizations.findEnabledLocationByNames(names.getFirst(), names.getLast()))
                    .orElseThrow(() -> invalid("省分或地市未在系统启用，或地市与省分不匹配"));
            var command = ProductionRules.validate(command(cells, location));
            if (!scope.writable() || !scope.contains(command.provinceId(), command.cityId()))
                throw new BusinessException(ProductionError.forbidden());
            return command;
        };
    }

    @Override
    protected Set<ProductionPlan.Command> existingDuplicates(List<ProductionPlan.Command> commands) {
        return plans.existingDuplicates(commands);
    }

    @Override
    protected UUID createValidated(UUID actor, Map<String, String> cells, ProductionPlan.Command command) {
        return plans.createImported(actor, command);
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
        return command(c, location);
    }

    private ProductionPlan.Command command(Map<String, String> c, OrganizationLocation location) {
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
                null,
                amount(c, 0),
                amount(c, 1),
                amount(c, 2),
                amount(c, 3),
                amount(c, 4),
                amount(c, 5),
                amount(c, 6),
                c.get("项目状态"),
                c.get("备注"),
                c.get("产品简介"));
    }

    private BigDecimal amount(Map<String, String> c, int index) {
        var value = c.getOrDefault(ProductionImport.AMOUNTS.get(index), "").strip();
        if (value.isEmpty()) return null;
        try {
            if (value.contains(",") && !value.matches("-?[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]+)?"))
                throw new NumberFormatException();
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            throw invalid(ProductionImport.fieldLabel(ProductionImport.AMOUNTS.get(index)) + "须为数字");
        }
    }
}
