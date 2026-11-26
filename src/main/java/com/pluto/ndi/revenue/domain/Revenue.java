package com.pluto.ndi.revenue.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** 来源和生效认定分别保存，待审值不会覆盖当前认定。 */
public final class Revenue {
    private Revenue() {}

    public static final String CATEGORY_FIELD = "三化分类";
    public static final String NETWORK = "网络化";
    public static final Set<String> REVIEWED_CATEGORIES = Set.of("数字化", "智能化");
    public static final Set<String> CATEGORIES = Set.of(NETWORK, "数字化", "智能化");

    public static final List<String> PROJECT_STATUSES = List.of("实施中", "已交付", "已上线");
    public static final List<String> INTEGRATED_ORGANIZATIONS = List.of("数科", "数智");

    public record Recognition(
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal amount,
            String reportedProjectName,
            String projectStatus,
            UUID integratedProvinceId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String integratedOrganization) {
        public Recognition(BigDecimal amount, String reportedProjectName, String projectStatus, UUID integratedProvinceId) {
            this(amount, reportedProjectName, projectStatus, integratedProvinceId, null);
        }
    }

    public record Entry(
            UUID id,
            long version,
            String month,
            String feeMonth,
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
