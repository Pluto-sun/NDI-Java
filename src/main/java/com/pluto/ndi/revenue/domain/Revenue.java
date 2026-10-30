package com.pluto.ndi.revenue.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** 来源和生效认定分别保存，待审值不会覆盖当前认定。 */
public final class Revenue {
    private Revenue() {}

    public record Recognition(
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal amount,
            String reportedProjectName,
            String projectStatus,
            UUID integratedProvinceId) {}

    public record Entry(
            UUID id,
            long version,
            String month,
            UUID provinceId,
            boolean specificOrganization,
            String contractNo,
            String clauseNo,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal clauseAmount,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal sourceAmount,
            Map<String, String> source,
            Recognition recognition,
            Instant createdAt,
            Instant recognizedAt,
            UUID recognizedBy,
            String state,
            UUID pendingRequestId,
            boolean canRecognize) {}

    public record Request(
            UUID id,
            UUID entryId,
            UUID initiatorId,
            UUID approverId,
            long baseVersion,
            long version,
            String status,
            Recognition proposed,
            String opinion,
            Instant createdAt,
            Instant decidedAt,
            String initiatorName,
            String approverName) {}

    public record History(
            UUID id,
            UUID actorId,
            String actorName,
            UUID requestId,
            Recognition beforeValue,
            Recognition afterValue,
            Instant occurredAt) {}

    public record Notice(
            UUID id,
            UUID entryId,
            UUID requestId,
            String kind,
            String message,
            Instant createdAt,
            Instant readAt) {}

    public record Page<T>(List<T> items, long totalElements, int totalPages, int page, int size) {}
}
