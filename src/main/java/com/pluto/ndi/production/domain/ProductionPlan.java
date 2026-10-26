package com.pluto.ndi.production.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 金额为空表示未排产，零表示已明确为零，两者分别保留。 */
public record ProductionPlan(
        UUID id,
        long version,
        String reviewerName,
        String planMonth,
        UUID provinceId,
        UUID cityId,
        String provinceName,
        String cityName,
        String projectName,
        String customerName,
        List<String> categories,
        BigDecimal networkAmount,
        BigDecimal gewuAmount,
        BigDecimal bigDataAmount,
        BigDecimal wangwuAmount,
        String projectStatus,
        Instant createdAt,
        Instant updatedAt,
        boolean writable) {
    public record Command(
            String reviewerName,
            String planMonth,
            UUID provinceId,
            UUID cityId,
            String projectName,
            String customerName,
            List<String> categories,
            BigDecimal networkAmount,
            BigDecimal gewuAmount,
            BigDecimal bigDataAmount,
            BigDecimal wangwuAmount,
            String projectStatus) {}
}
