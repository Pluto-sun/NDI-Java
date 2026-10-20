package com.pluto.ndi.projects.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Excel 预检后的标准化数据；源文件原貌另存于导入审计行。 */
public record ProjectImportCandidate(
        UUID provinceId, UUID cityId, String projectName, String customerName,
        String industryCode, String customerNeed, BigDecimal totalAmount,
        UUID provinceLeaderUserId, String cityAccountManagerName,
        String signingSubjectCode, String stageCode, List<CategoryAmount> categories, List<Progress> progress,
        Instant occurredAt, List<String> productSupportExpertNames) {
    public record CategoryAmount(String code, BigDecimal amount) { }
    public record Progress(Instant occurredAt, String content) { }
}
