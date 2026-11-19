package com.pluto.ndi.production.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal networkOrderAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gewuOrderAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gewuRevenueAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal bigDataOrderAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal bigDataRevenueAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal wangwuOrderAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal wangwuRevenueAmount,
        String projectStatus,
        String remark,
        String productIntroduction,
        Instant createdAt,
        Instant updatedAt,
        boolean writable) {
    @JsonProperty
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal networkAmount() {
        return networkOrderAmount;
    }

    @JsonProperty
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal gewuAmount() {
        return sum(gewuOrderAmount, gewuRevenueAmount);
    }

    @JsonProperty
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal bigDataAmount() {
        return sum(bigDataOrderAmount, bigDataRevenueAmount);
    }

    @JsonProperty
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal wangwuAmount() {
        return sum(wangwuOrderAmount, wangwuRevenueAmount);
    }

    private static BigDecimal sum(BigDecimal order, BigDecimal revenue) {
        if (order == null) return revenue;
        return revenue == null ? order : order.add(revenue);
    }

    public record Command(
            String reviewerName,
            String planMonth,
            UUID provinceId,
            UUID cityId,
            String projectName,
            String customerName,
            List<String> categories,
            BigDecimal networkOrderAmount,
            BigDecimal gewuOrderAmount,
            BigDecimal gewuRevenueAmount,
            BigDecimal bigDataOrderAmount,
            BigDecimal bigDataRevenueAmount,
            BigDecimal wangwuOrderAmount,
            BigDecimal wangwuRevenueAmount,
            String projectStatus,
            String remark,
            String productIntroduction) {}
}
