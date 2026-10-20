package com.pluto.ndi.projects.repository;

import java.util.UUID;

/** 重名检查仅加载必要字段，避免整张项目主档进入内存。 */
public record ProjectNameCandidate(UUID projectId, String projectCode, String projectName) { }
