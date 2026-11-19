package com.pluto.ndi.capabilities;

import com.pluto.ndi.shared.excel.ExcelImportLimits;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

import static com.pluto.ndi.capabilities.GewuDeploymentService.failure;
import static com.pluto.ndi.capabilities.GewuDeploymentService.invalid;

@Service
class MonthlyTokenImport {
    private final IntelligenceService agents;

    MonthlyTokenImport(IntelligenceService agents) {
        this.agents = agents;
    }

    public record Row(String key, int sourceRow, String province, String agent, String month,
            String amount, boolean screenshot, String status, String message, String warning) {}
    public record Preview(String sheet, List<String> sheets, List<Row> rows) {}
    private record Checked(Row row, UUID agent, UUID existing, MonthlyTokenWorkbook.Image image) {}
    private record AgentMonth(UUID agent, String month) {}

    public Preview preview(UUID actor, byte[] file, String sheet, int year) {
        agents.scope(actor);
        var workbook = workbook(file, sheet, year);
        return new Preview(workbook.sheet(), workbook.sheets(),
                check(actor, workbook, year).stream().map(Checked::row).toList());
    }

    @Transactional
    public int confirm(UUID actor, byte[] file, String sheet, int year, List<String> selection) {
        agents.scope(actor);
        if (selection == null || selection.isEmpty() || selection.size() > ExcelImportLimits.MAX_ROWS
                || new HashSet<>(selection).size() != selection.size())
            throw invalid("请选择 1 至 2000 条通过预检的记录");
        var selected = Set.copyOf(selection);
        var rows = check(actor, workbook(file, sheet, year), year).stream()
                .filter(row -> selected.contains(row.row().key()) && row.row().status().equals("READY"))
                .sorted(Comparator.comparing(Checked::agent).thenComparing(row -> row.row().month()))
                .toList();
        if (rows.size() != selected.size())
            throw failure(HttpStatus.CONFLICT, "所选记录已变化，请重新预检");
        for (var item : rows) {
            var image = item.image();
            agents.importToken(actor, item.agent(), item.row().month(), item.row().amount(),
                    item.existing(), image == null ? null : image.type(), image == null ? null : image.data());
        }
        return rows.size();
    }

    private List<Checked> check(UUID actor, MonthlyTokenWorkbook.Content workbook, int year) {
        var matches = new HashMap<Integer, List<IntelligenceService.Entry>>();
        var history = new HashMap<UUID, List<IntelligenceService.Token>>();
        var counts = new HashMap<AgentMonth, Integer>();
        var result = new ArrayList<Checked>();
        for (var line : workbook.lines()) {
            var choices = matches.computeIfAbsent(line.row(), row ->
                    agents.matchTokenAgents(actor, line.province(), line.agent(), line.project(), line.systemId()));
            String month = "%04d-%02d".formatted(year, line.month());
            var agent = choices.size() == 1 ? choices.getFirst() : null;
            UUID agentId = agent == null ? null : agent.id();
            String error = line.error();
            if (error.isEmpty() && agent == null) {
                error = choices.isEmpty() ? "未找到有权限的智能体，请核对省分、智能体名称和应用项目名称，或填写系统 ID"
                        : "存在重名智能体，请补齐应用项目名称或填写系统 ID";
            }
            String status = "FAILED";
            String message = error;
            UUID existing = null;
            if (agentId != null) counts.merge(new AgentMonth(agentId, month), 1, Integer::sum);
            if (error.isEmpty()) {
                try {
                    var amount = IntelligenceService.decimal(line.amount(), "Token 量");
                    var old = history.computeIfAbsent(agentId, id -> agents.tokens(actor, id)).stream()
                            .filter(item -> item.month().equals(month)).findFirst().orElse(null);
                    if (old == null) {
                        status = "READY";
                        message = "新增月度记录";
                    } else if (amount.compareTo(IntelligenceService.decimal(old.amount(), "Token 量")) != 0) {
                        message = "同月已登记不同用量，已保留原记录";
                    } else if (old.screenshotId() == null && line.image() != null) {
                        status = "READY";
                        message = "为已有记录补充截图";
                        existing = old.id();
                    } else {
                        status = "SKIPPED";
                        message = "已有相同用量";
                    }
                } catch (com.pluto.ndi.shared.web.BusinessException e) {
                    message = e.getMessage();
                }
            }
            var key = line.row() + ":" + month + ":" + agentId + ":" + existing;
            var row = new Row(key, line.row(), agent == null ? line.province() : agent.fields().get("省分"),
                    agent == null ? line.agent() : agent.fields().getOrDefault("智能体名称", ""), month,
                    line.amount(), line.image() != null, status, message, line.warning());
            result.add(new Checked(row, agentId, existing, line.image()));
        }
        return result.stream().map(item -> {
            var row = item.row();
            if (item.agent() == null || counts.get(new AgentMonth(item.agent(), row.month())) < 2) return item;
            return new Checked(new Row(row.key(), row.sourceRow(), row.province(), row.agent(), row.month(),
                    row.amount(), row.screenshot(), "FAILED", "文件内存在同一智能体同月的重复记录", row.warning()),
                    item.agent(), item.existing(), item.image());
        }).toList();
    }

    private MonthlyTokenWorkbook.Content workbook(byte[] file, String sheet, int year) {
        if (year < 1900 || year > 9999) throw invalid("请填写四位统计年份");
        try {
            return MonthlyTokenWorkbook.read(file, sheet);
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        } catch (IOException e) {
            throw invalid("无法读取 Excel 文件，请检查工作簿格式");
        }
    }
}
