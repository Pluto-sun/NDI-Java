package com.pluto.ndi.projects.controller;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 仅确认用户明确选中的预检通过行。 */
public record ProjectImportConfirmRequest(@NotEmpty @Size(max = 1000) List<Integer> selectedRowNumbers) { }
