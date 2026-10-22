package com.pluto.ndi.projects.domain;
import java.util.UUID;
import java.time.Instant;

/** 导出附加字段的标量投影，不加载项目正文和关联集合。 */
public record ProjectExportDetails(UUID id, UUID provinceId, UUID cityId, UUID provinceLeaderUserId,
                                   String signingSubjectCode, String cityAccountManagerName, Instant updatedAt) { }
