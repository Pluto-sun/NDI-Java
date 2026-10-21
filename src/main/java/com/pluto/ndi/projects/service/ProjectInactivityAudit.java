package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;

/** 仅包含督办业务信息，不向项目读者暴露其他系统审计内容。 */
public record ProjectInactivityAudit(UUID id, String action, String actorName, String note, Instant occurredAt) { }
