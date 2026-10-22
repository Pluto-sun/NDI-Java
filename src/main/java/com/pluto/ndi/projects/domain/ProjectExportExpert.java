package com.pluto.ndi.projects.domain;
import java.util.UUID;

/** 保持项目中既有顺序的专家姓名投影。 */
public record ProjectExportExpert(UUID projectId, String name) { }
