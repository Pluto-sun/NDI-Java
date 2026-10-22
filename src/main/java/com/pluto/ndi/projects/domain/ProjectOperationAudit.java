package com.pluto.ndi.projects.domain;

import java.time.Instant;

/** 普通操作审计仅输出业务摘要和操作人，不返回原始事件载荷。 */
public record ProjectOperationAudit(String id, String eventType, String summary,
                                    String operatorName, Instant occurredAt) { }
