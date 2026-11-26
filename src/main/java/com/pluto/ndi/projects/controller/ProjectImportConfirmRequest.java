package com.pluto.ndi.projects.controller;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** 仅确认用户明确选中的预检通过行。 */
public record ProjectImportConfirmRequest(
        @NotEmpty
        List<Integer> selectedRowNumbers) {}
