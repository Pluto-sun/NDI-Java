package com.pluto.ndi.production.mapper;

import com.pluto.ndi.shared.reporting.DashboardSummary;
import com.pluto.ndi.shared.reporting.SchedulingOverviewSource;
import com.pluto.ndi.shared.reporting.DashboardSummary.Metric;
import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.production.domain.ProductionPlan;
import com.pluto.ndi.production.service.ProductionAccessService.Access;
import com.pluto.ndi.production.util.ProductionRules;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;

@Repository
public class ProductionPlanMapper {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProductionPlanMapper(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Filter(
            String month,
            UUID provinceId,
            UUID cityId,
            String search,
            String category,
            int page,
            int size) {}

    public record Result(
            List<ProductionPlan> items, long totalElements, int totalPages, int page, int size) {}

    private static final String SELECT =
            """
SELECT BIN_TO_UUID(p.id) id,p.*,BIN_TO_UUID(p.province_id) province,BIN_TO_UUID(p.city_id) city,
  v.province_name province_name,c.city_name city_name FROM production_plan p
  JOIN organization_province v ON v.id=p.province_id JOIN organization_city c ON c.id=p.city_id
""";

    private static final String PRODUCT_TOTALS = """
            COALESCE(p.network_order_amount,0) AS network,
            COALESCE(p.gewu_order_amount,0)+COALESCE(p.gewu_revenue_amount,0) AS gewu,
            COALESCE(p.big_data_order_amount,0)+COALESCE(p.big_data_revenue_amount,0) AS big_data,
            COALESCE(p.wangwu_order_amount,0)+COALESCE(p.wangwu_revenue_amount,0) AS wangwu
            """;

    private StringBuilder scopeWhere(Access access, MapSqlParameterSource args) {
        var where = new StringBuilder(" WHERE p.deleted_at IS NULL");
        switch (access.scope().level()) {
            case NATIONAL -> {}
            case PROVINCE -> {
                where.append(" AND p.province_id IN (:scope)");
                args.addValue(
                        "scope",
                        access.scope().provinceIds().stream()
                                .map(ProductionPlanMapper::bytes)
                                .toList());
            }
            case CITY -> {
                where.append(" AND p.city_id=UUID_TO_BIN(:scope)");
                args.addValue("scope", access.scope().organizationId().toString());
            }
        }
        return where;
    }

    public DashboardSummary dashboard(Access access, DashboardSummary.MonthRange months) {
        var args = new MapSqlParameterSource();
        var where = scopeWhere(access, args);
        if (months.from() != null) {
            where.append(" AND p.plan_month>=:start");
            args.addValue("start", months.from());
        }
        if (months.to() != null) {
            where.append(" AND p.plan_month<=:finish");
            args.addValue("finish", months.to());
        }
        var names = List.of("5G工业专网", "格物工业互联网平台", "工业大数据平台", "万悟工业智能体平台");
        var keys = List.of("network", "gewu", "big_data", "wangwu");
        var counts = new long[keys.size()];
        var totals = new BigDecimal[keys.size()];
        Arrays.fill(totals, BigDecimal.ZERO);
        var provinces = jdbc.query("""
                SELECT province_name, COUNT(*) AS total_count,
                    SUM(network>0) AS network_count, SUM(network) AS network_amount, SUM(gewu>0) AS gewu_count, SUM(gewu) AS gewu_amount, SUM(big_data>0) AS big_data_count, SUM(big_data) AS big_data_amount, SUM(wangwu>0) AS wangwu_count, SUM(wangwu) AS wangwu_amount
                FROM (
                    SELECT v.province_name,
                """ + PRODUCT_TOTALS
                + " FROM production_plan p JOIN organization_province v ON v.id=p.province_id"
                + where + ") scoped GROUP BY province_name", args, (rs, row) -> {
                    BigDecimal provinceAmount = BigDecimal.ZERO;
                    for (int i = 0; i < keys.size(); i++) {
                        counts[i] += rs.getLong(keys.get(i) + "_count");
                        var amount = rs.getBigDecimal(keys.get(i) + "_amount");
                        totals[i] = totals[i].add(amount);
                        provinceAmount = provinceAmount.add(amount);
                    }
                    return new Metric(rs.getString("province_name"), rs.getLong("total_count"), provinceAmount);
                });
        var categories = new ArrayList<Metric>();
        for (int i = 0; i < keys.size(); i++) categories.add(new Metric(names.get(i), counts[i], totals[i]));
        return DashboardSummary.amounts(categories, provinces);
    }

    public SchedulingOverviewSource schedulingOverview(Access access, DashboardSummary.MonthRange months) {
        var args = new MapSqlParameterSource("start", months.from()).addValue("finish", months.to());
        var where = scopeWhere(access, args).append(" AND p.plan_month BETWEEN :start AND :finish");
        var source = " FROM production_plan p JOIN organization_province v ON v.id=p.province_id" + where;
        var groups = jdbc.query("""
                SELECT plan_month AS month, province_id, province_name,
                    SUM(network) AS network, SUM(gewu) AS digital, SUM(big_data+wangwu) AS intelligent
                FROM (SELECT p.plan_month, BIN_TO_UUID(v.id) AS province_id, v.province_name,
                """ + PRODUCT_TOTALS + source + ") scoped GROUP BY plan_month,province_id,province_name",
                args, (rs, row) ->
                new SchedulingOverviewSource.Group(rs.getDate("month").toLocalDate(),
                        rs.getString("province_id"), rs.getString("province_name"), Map.of(
                                "网络化", rs.getBigDecimal("network"), "数字化", rs.getBigDecimal("digital"),
                                "智能化", rs.getBigDecimal("intelligent"))));
        long count = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT CAST(NULLIF(TRIM(p.project_name),'') AS BINARY))" + source,
                args, Long.class);
        return new SchedulingOverviewSource(count, groups);
    }

    public Result list(Access access, Filter filter) {
        var args = new MapSqlParameterSource();
        var where = scopeWhere(access, args);
        if (filter.month() != null && !filter.month().isBlank()) {
            where.append(" AND p.plan_month=:month");
            args.addValue("month", filter.month() + "-01");
        }
        if (filter.provinceId() != null) {
            where.append(" AND p.province_id=UUID_TO_BIN(:province)");
            args.addValue("province", filter.provinceId().toString());
        }
        if (filter.cityId() != null) {
            where.append(" AND p.city_id=UUID_TO_BIN(:city)");
            args.addValue("city", filter.cityId().toString());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            where.append(
                    " AND (p.project_name LIKE :term ESCAPE '!' OR p.customer_name LIKE :term"
                        + " ESCAPE '!' OR p.reviewer_name LIKE :term ESCAPE '!')");
            args.addValue(
                    "term",
                    "%"
                            + filter.search()
                                    .strip()
                                    .replace("!", "!!")
                                    .replace("%", "!%")
                                    .replace("_", "!_")
                            + "%");
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            where.append(" AND JSON_CONTAINS(p.categories,JSON_QUOTE(:category))");
            args.addValue("category", filter.category());
        }
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM production_plan p" + where, args, Long.class);
        args.addValue("limit", filter.size()).addValue("offset", (long) filter.page() * filter.size());
        var rows =
                jdbc.query(
                        SELECT
                                + where
                                + " ORDER BY p.plan_month DESC,p.created_at DESC,p.id LIMIT :limit"
                                + " OFFSET :offset",
                        args,
                        (rs, n) -> row(rs, access.writable()));
        return new Result(
                rows,
                count,
                (int) ((count + filter.size() - 1) / filter.size()),
                filter.page(),
                filter.size());
    }

    public Optional<ProductionPlan> find(UUID id, boolean lock) {
        return find(id, lock, false);
    }

    public Optional<ProductionPlan> find(UUID id, boolean lock, boolean writable) {
        var rows =
                jdbc.query(
                        SELECT
                                + " WHERE p.id=UUID_TO_BIN(:id) AND p.deleted_at IS NULL"
                                + (lock ? " FOR UPDATE" : ""),
                        Map.of("id", id.toString()),
                        (rs, n) -> row(rs, writable));
        return rows.stream().findFirst();
    }

    public boolean duplicate(ProductionPlan.Command c, UUID except) {
        return jdbc.queryForObject(
                        """
SELECT COUNT(*) FROM production_plan WHERE deleted_at IS NULL AND plan_month=:month AND province_id=UUID_TO_BIN(:province)
 AND city_id=UUID_TO_BIN(:city) AND normalized_project=:project AND normalized_customer=:customer AND id<>UUID_TO_BIN(:id)
""",
                        new MapSqlParameterSource("month", c.planMonth() + "-01")
                                .addValue("province", c.provinceId().toString())
                                .addValue("city", c.cityId().toString())
                                .addValue("project", ProductionRules.normalize(c.projectName()))
                                .addValue("customer", ProductionRules.normalize(c.customerName()))
                                .addValue("id", except.toString()),
                        Long.class)
                > 0;
    }

    public void save(UUID id, UUID actor, ProductionPlan.Command c, boolean create, Instant now) {
        var args =
                new MapSqlParameterSource("id", id.toString())
                        .addValue("actor", actor.toString())
                        .addValue("reviewer", c.reviewerName())
                        .addValue("month", c.planMonth() + "-01")
                        .addValue("province", c.provinceId().toString())
                        .addValue("city", c.cityId().toString())
                        .addValue("project", c.projectName())
                        .addValue("customer", c.customerName())
                        .addValue("np", ProductionRules.normalize(c.projectName()))
                        .addValue("nc", ProductionRules.normalize(c.customerName()))
                        .addValue("categories", encode(c.categories()))
                        .addValue("networkOrderAmount", c.networkOrderAmount())
                        .addValue("gewuOrderAmount", c.gewuOrderAmount())
                        .addValue("gewuRevenueAmount", c.gewuRevenueAmount())
                        .addValue("bigDataOrderAmount", c.bigDataOrderAmount())
                        .addValue("bigDataRevenueAmount", c.bigDataRevenueAmount())
                        .addValue("wangwuOrderAmount", c.wangwuOrderAmount())
                        .addValue("wangwuRevenueAmount", c.wangwuRevenueAmount())
                        .addValue("status", c.projectStatus())
                        .addValue("remark", c.remark())
                        .addValue("introduction", c.productIntroduction())
                        .addValue("now", java.sql.Timestamp.from(now));
        if (create)
            jdbc.update(
                    """
INSERT INTO production_plan(id,reviewer_name,plan_month,province_id,city_id,project_name,customer_name,normalized_project,normalized_customer,categories,
 network_order_amount,gewu_order_amount,gewu_revenue_amount,big_data_order_amount,big_data_revenue_amount,wangwu_order_amount,wangwu_revenue_amount,project_status,remark,product_introduction,created_by,created_at,updated_at)
VALUES(UUID_TO_BIN(:id),:reviewer,:month,UUID_TO_BIN(:province),UUID_TO_BIN(:city),:project,:customer,:np,:nc,CAST(:categories AS JSON),
 :networkOrderAmount,:gewuOrderAmount,:gewuRevenueAmount,:bigDataOrderAmount,:bigDataRevenueAmount,:wangwuOrderAmount,:wangwuRevenueAmount,:status,:remark,:introduction,UUID_TO_BIN(:actor),:now,:now)
""",
                    args);
        else
            jdbc.update(
                    """
UPDATE production_plan SET version_no=version_no+1,reviewer_name=:reviewer,plan_month=:month,province_id=UUID_TO_BIN(:province),city_id=UUID_TO_BIN(:city),
 project_name=:project,customer_name=:customer,normalized_project=:np,normalized_customer=:nc,categories=CAST(:categories AS JSON),
 network_order_amount=:networkOrderAmount,gewu_order_amount=:gewuOrderAmount,gewu_revenue_amount=:gewuRevenueAmount,big_data_order_amount=:bigDataOrderAmount,big_data_revenue_amount=:bigDataRevenueAmount,wangwu_order_amount=:wangwuOrderAmount,wangwu_revenue_amount=:wangwuRevenueAmount,project_status=:status,remark=:remark,product_introduction=:introduction,updated_at=:now WHERE id=UUID_TO_BIN(:id)
""",
                    args);
    }

    public void delete(UUID id, Instant now) {
        jdbc.update(
                "UPDATE production_plan SET deleted_at=:now,updated_at=:now,version_no=version_no+1"
                    + " WHERE id=UUID_TO_BIN(:id)",
                Map.of("id", id.toString(), "now", java.sql.Timestamp.from(now)));
    }

    private ProductionPlan row(ResultSet rs, boolean writable) throws SQLException {
        List<String> categories;
        try {
            categories =
                    json.readValue(
                            rs.getString("categories"),
                            json.getTypeFactory()
                                    .constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            throw new IllegalStateException("排产类别无法读取", e);
        }
        return new ProductionPlan(
                UUID.fromString(rs.getString("id")),
                rs.getLong("version_no"),
                rs.getString("reviewer_name"),
                rs.getDate("plan_month").toLocalDate().toString().substring(0, 7),
                UUID.fromString(rs.getString("province")),
                UUID.fromString(rs.getString("city")),
                rs.getString("province_name"),
                rs.getString("city_name"),
                rs.getString("project_name"),
                rs.getString("customer_name"),
                categories,
                amount(rs, "network_order_amount"),
                amount(rs, "gewu_order_amount"),
                amount(rs, "gewu_revenue_amount"),
                amount(rs, "big_data_order_amount"),
                amount(rs, "big_data_revenue_amount"),
                amount(rs, "wangwu_order_amount"),
                amount(rs, "wangwu_revenue_amount"),
                rs.getString("project_status"),
                rs.getString("remark"),
                rs.getString("product_introduction"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                writable);
    }

    private static java.math.BigDecimal amount(ResultSet row, String column) throws SQLException {
        var value = row.getBigDecimal(column);
        if (value == null) return null;
        // 兼容原有的两位小数表示，同时保留新增的有效小数位。
        var decimal = value.stripTrailingZeros();
        return decimal.setScale(Math.max(2, decimal.scale()));
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(UUID id) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
