package com.pluto.ndi.projects.util;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.pluto.ndi.projects.domain.ProjectApplicationScenario;

/** 可选规划属性的统一校验，接口和 Excel 导入共用。 */
public final class ProjectPlanningFields {
    private static final Pattern MONTH = Pattern.compile("^(\\d{4})[-/](\\d{1,2})$");
    private static final Pattern DAY = Pattern.compile("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$");

    private ProjectPlanningFields() { }

    public static List<String> normalizeScenarios(List<String> codes) {
        if (codes == null) return List.of();
        var result = new ArrayList<String>();
        Set<String> seen = new HashSet<>();
        for (var code : codes) {
            if (ProjectApplicationScenario.fromCode(code).isEmpty() || !seen.add(code)) {
                throw new IllegalArgumentException("应用场景代码无效或重复");
            }
            result.add(code);
        }
        return List.copyOf(result);
    }

    public static List<String> parseScenarios(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        var codes = new ArrayList<String>();
        for (var item : raw.split("[,，、;；\\n]+")) {
            if (item.isBlank()) continue;
            var label = item.trim();
            var scenario = ProjectApplicationScenario.fromCode(label)
                    .or(() -> ProjectApplicationScenario.fromLabel(label))
                    .orElseThrow(() -> new IllegalArgumentException("应用场景未识别：" + label));
            codes.add(scenario.name());
        }
        return normalizeScenarios(codes);
    }

    public static LocalDate parseBidMonth(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var text = raw.trim();
        var month = MONTH.matcher(text);
        var day = DAY.matcher(text);
        try {
            if (month.matches()) return YearMonth.of(Integer.parseInt(month.group(1)),
                    Integer.parseInt(month.group(2))).atDay(1);
            if (day.matches()) {
                var date = LocalDate.of(Integer.parseInt(day.group(1)), Integer.parseInt(day.group(2)),
                        Integer.parseInt(day.group(3)));
                return date.withDayOfMonth(1);
            }
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("拟招标日期不是有效月份", exception);
        }
        throw new IllegalArgumentException("拟招标日期应为 YYYY-MM 或 YYYY-MM-DD");
    }
}
