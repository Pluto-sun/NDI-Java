package com.pluto.ndi.shared.excel;

import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;
import java.util.Map;

/** 使用隐藏字典和命名区域生成下拉，避免内联选项的长度限制，不使用宏。 */
public final class ExcelDropdowns {
    private final XSSFWorkbook book;
    private final XSSFSheet target;
    private final XSSFSheet options;
    private final List<String> headers;
    private int nextColumn;

    public ExcelDropdowns(XSSFWorkbook book, XSSFSheet target, List<String> headers) {
        this.book = book;
        this.target = target;
        this.headers = List.copyOf(headers);
        this.options = book.createSheet("导入选项");
        book.setSheetVisibility(book.getSheetIndex(options), SheetVisibility.HIDDEN);
    }

    public void list(String field, List<String> values) {
        add(field, writeList(field, values), "请从下拉列表选择系统选项");
    }

    /** 父级名称映射到子级命名区域；每行只读取该行的父级单元格。 */
    public void dependent(String parent, String child, Map<String, List<String>> groups) {
        list(parent, List.copyOf(groups.keySet()));
        String empty = writeList("未选择父级", List.of());
        int column = nextColumn;
        nextColumn += 2;
        cell(0, column, parent);
        cell(0, column + 1, child + "选项区域");
        int row = 1;
        for (var group : groups.entrySet()) {
            String range = writeList(child + "-" + group.getKey(), group.getValue());
            cell(row, column, group.getKey());
            cell(row++, column + 1, range);
        }
        String mapping = "_ndi_mapping_" + column;
        name(mapping, column, column + 1, Math.max(1, groups.size()));
        String parentCell = "$" + CellReference.convertNumToColString(index(parent)) + "2";
        add(child, "INDIRECT(IFERROR(VLOOKUP(" + parentCell + "," + mapping
                        + ",2,FALSE),\"" + empty + "\"))",
                "先选择" + parent + "；修改后请重新选择" + child);
    }

    private String writeList(String field, List<String> values) {
        int column = nextColumn++;
        var distinct = values.stream().filter(value -> value != null && !value.isBlank())
                .distinct().toList();
        cell(0, column, field);
        for (int row = 0; row < distinct.size(); row++) cell(row + 1, column, distinct.get(row));
        String range = "_ndi_options_" + column;
        name(range, column, column, Math.max(1, distinct.size()));
        return range;
    }

    private void name(String value, int startColumn, int endColumn, int count) {
        var range = book.createName();
        range.setNameName(value);
        range.setRefersToFormula("'" + options.getSheetName() + "'!$"
                + CellReference.convertNumToColString(startColumn) + "$2:$"
                + CellReference.convertNumToColString(endColumn) + "$" + (count + 1));
    }

    private void cell(int row, int column, String value) {
        var targetRow = options.getRow(row);
        if (targetRow == null) targetRow = options.createRow(row);
        targetRow.createCell(column).setCellValue(value);
    }

    private void add(String field, String formula, String prompt) {
        var helper = target.getDataValidationHelper();
        var validation = helper.createValidation(helper.createFormulaListConstraint(formula),
                new CellRangeAddressList(1, 1000, index(field), index(field)));
        // XSSF 将该开关反向写入 showDropDown，true 表示显示下拉箭头。
        validation.setSuppressDropDownArrow(true);
        validation.setEmptyCellAllowed(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.setShowErrorBox(true);
        validation.createErrorBox("请选择有效选项", field + "必须从下拉列表选择；粘贴数据仍需通过导入预检");
        validation.setShowPromptBox(true);
        validation.createPromptBox(field, prompt);
        target.addValidationData(validation);
    }

    private int index(String field) {
        int column = headers.indexOf(field);
        if (column < 0) throw new IllegalArgumentException("模板不存在下拉字段：" + field);
        return column;
    }
}
