package com.pluto.ndi.projects.service;

import java.time.Instant;
import java.util.UUID;
import com.pluto.ndi.projects.domain.ApprovalStatus;

/** 撤销仅返回本人操作结果，避免调动后的原发起人经操作响应读取范围外项目内容。 */
public record ApprovalWithdrawalSummary(UUID id, ApprovalStatus status, Instant decidedAt) { }
