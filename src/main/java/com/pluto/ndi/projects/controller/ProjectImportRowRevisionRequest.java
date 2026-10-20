package com.pluto.ndi.projects.controller;

import java.util.Map;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ProjectImportRowRevisionRequest(@NotNull @Min(0) Integer expectedRevision,
        @NotEmpty Map<String, @NotNull String> changes) { }
