package com.pluto.ndi.projects.controller;

import com.pluto.ndi.shared.excel.ExcelImportLimits;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 仅确认用户明确选中的预检通过行。 */
public record ProjectImportConfirmRequest(
        @NotEmpty @Size(max = ExcelImportLimits.MAX_ROWS, message = "最多选择 2000 条预检通过的记录")
        List<Integer> selectedRowNumbers) {}
