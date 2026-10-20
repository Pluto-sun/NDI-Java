package com.pluto.ndi.projects.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 未完成项目表单的保存输入。 */
public record ProjectDraftRequest(
        @Size(max = 200) String title,
        @NotNull JsonNode data
) { }
