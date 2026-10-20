package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/** 仅向草稿所有者展示的表单数据。 */
public record ProjectDraftSummary(UUID id, String title, JsonNode data, Instant createdAt, Instant updatedAt) { }
