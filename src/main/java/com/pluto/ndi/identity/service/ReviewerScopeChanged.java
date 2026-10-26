package com.pluto.ndi.identity.service;

import java.util.Set;
import java.util.UUID;

/** 同步事务事件：范围调整与业务待办交接必须同时提交。 */
public record ReviewerScopeChanged(UUID actorId, Set<UUID> provinceIds) {}
