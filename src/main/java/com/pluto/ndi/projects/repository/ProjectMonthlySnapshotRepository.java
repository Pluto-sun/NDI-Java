package com.pluto.ndi.projects.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.projects.domain.ProjectMonthlyRevision;
import com.pluto.ndi.projects.domain.ProjectCategoryTotals;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 月末快照、统计事件与修订记录的数据库访问。 */
@Repository
public class ProjectMonthlySnapshotRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public ProjectMonthlySnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public void initialize(YearMonth activationMonth) {
        jdbc.update("""
                INSERT INTO project_monthly_snapshot_control (id, activation_month)
                VALUES (1, ?) ON DUPLICATE KEY UPDATE id = id
                """, date(activationMonth));
    }

    public YearMonth activationMonth() {
        return YearMonth.from(jdbc.queryForObject(
                "SELECT activation_month FROM project_monthly_snapshot_control WHERE id = 1",
                Date.class).toLocalDate());
    }

    public Optional<YearMonth> lastCompletedMonth() {
        var value = jdbc.queryForObject(
                "SELECT last_completed_month FROM project_monthly_snapshot_control WHERE id = 1", Date.class);
        return value == null ? Optional.empty() : Optional.of(YearMonth.from(value.toLocalDate()));
    }

    public void lockControl() {
        jdbc.queryForObject("SELECT id FROM project_monthly_snapshot_control WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public void complete(YearMonth month) {
        jdbc.update("UPDATE project_monthly_snapshot_control SET last_completed_month = ? WHERE id = 1", date(month));
    }

    public void lockProject(UUID projectId) {
        jdbc.queryForObject("SELECT id FROM project_project WHERE id = UUID_TO_BIN(?) FOR UPDATE",
                (rs, rowNum) -> rs.getBytes(1), projectId.toString());
    }

    public void insertEvent(Event event) {
        jdbc.update("""
                INSERT INTO project_monthly_state_event
                    (id, project_id, effective_month, recorded_at, source_kind, source_id, reason,
                     baseline, province_id, city_id, industry_code, stage_code, total_amount,
                     network_amount, gewu_amount, big_data_amount, wangwu_amount)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, UUID_TO_BIN(?), ?, ?,
                        UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """, UUID.randomUUID().toString(), event.projectId().toString(), date(event.month()),
                Timestamp.from(event.recordedAt()), event.sourceKind(), event.sourceId().toString(),
                event.reason(), event.baseline(), id(event.provinceId()), id(event.cityId()),
                event.industryCode(), event.stageCode(), event.totalAmount(),
                event.categories() == null ? null : event.categories().network(),
                event.categories() == null ? null : event.categories().gewu(),
                event.categories() == null ? null : event.categories().bigData(),
                event.categories() == null ? null : event.categories().wangwu());
    }

    public boolean hasBaseline(UUID projectId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM project_monthly_state_event
                              WHERE project_id = UUID_TO_BIN(?) AND baseline = TRUE)
                """, Boolean.class, projectId.toString()));
    }

    public List<UUID> projectIdsThrough(YearMonth month) {
        return jdbc.query("""
                SELECT BIN_TO_UUID(project_id) FROM project_monthly_state_event
                WHERE baseline = TRUE AND effective_month <= ? ORDER BY project_id
                """, (rs, rowNum) -> UUID.fromString(rs.getString(1)), date(month));
    }

    public List<Event> eventsThrough(UUID projectId, YearMonth month) {
        return jdbc.query("""
                SELECT effective_month, recorded_at, source_kind, BIN_TO_UUID(source_id) AS source_id,
                       reason, baseline, BIN_TO_UUID(province_id) AS province_id,
                       BIN_TO_UUID(city_id) AS city_id, industry_code, stage_code, total_amount
                       ,network_amount, gewu_amount, big_data_amount, wangwu_amount
                FROM project_monthly_state_event
                WHERE project_id = UUID_TO_BIN(?) AND effective_month <= ?
                ORDER BY effective_month, baseline DESC, recorded_at, id
                """, (rs, rowNum) -> new Event(projectId,
                        YearMonth.from(rs.getDate("effective_month").toLocalDate()),
                        rs.getTimestamp("recorded_at").toInstant(), rs.getString("source_kind"),
                        UUID.fromString(rs.getString("source_id")), rs.getString("reason"),
                        rs.getBoolean("baseline"), uuid(rs.getString("province_id")),
                        uuid(rs.getString("city_id")), rs.getString("industry_code"),
                        rs.getString("stage_code"), rs.getBigDecimal("total_amount"),
                        categories(rs.getBigDecimal("network_amount"), rs.getBigDecimal("gewu_amount"),
                                rs.getBigDecimal("big_data_amount"), rs.getBigDecimal("wangwu_amount"))),
                projectId.toString(), date(month));
    }

    public List<YearMonth> existingMonths(UUID projectId, YearMonth from) {
        return jdbc.query("""
                SELECT month_start FROM project_monthly_snapshot
                WHERE project_id = UUID_TO_BIN(?) AND month_start >= ? ORDER BY month_start
                """, (rs, rowNum) -> YearMonth.from(rs.getDate(1).toLocalDate()),
                projectId.toString(), date(from));
    }

    public Optional<Snapshot> find(UUID projectId, YearMonth month) {
        return jdbc.query("""
                SELECT BIN_TO_UUID(province_id) AS province_id, BIN_TO_UUID(city_id) AS city_id,
                       city_name, industry_code, industry_name, stage_code, total_amount,
                       network_amount, gewu_amount, big_data_amount, wangwu_amount, revision_no
                FROM project_monthly_snapshot WHERE project_id = UUID_TO_BIN(?) AND month_start = ?
                """, (rs, rowNum) -> new Snapshot(projectId, month,
                        UUID.fromString(rs.getString("province_id")), UUID.fromString(rs.getString("city_id")),
                        rs.getString("city_name"), rs.getString("industry_code"),
                        rs.getString("industry_name"), rs.getString("stage_code"),
                        rs.getBigDecimal("total_amount"), new ProjectCategoryTotals(
                                rs.getBigDecimal("network_amount"), rs.getBigDecimal("gewu_amount"),
                                rs.getBigDecimal("big_data_amount"), rs.getBigDecimal("wangwu_amount")),
                        rs.getInt("revision_no")),
                projectId.toString(), date(month)).stream().findFirst();
    }

    public void insert(Snapshot item, Instant at) {
        jdbc.update("""
                INSERT INTO project_monthly_snapshot
                    (month_start, project_id, province_id, city_id, city_name, industry_code,
                     industry_name, stage_code, total_amount, network_amount, gewu_amount,
                     big_data_amount, wangwu_amount, revision_no, updated_at)
                VALUES (?, UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                """, date(item.month()), item.projectId().toString(), item.provinceId().toString(),
                item.cityId().toString(), item.cityName(), item.industryCode(), item.industryName(),
                item.stageCode(), item.totalAmount(), item.categories().network(), item.categories().gewu(),
                item.categories().bigData(), item.categories().wangwu(), Timestamp.from(at));
    }

    public void revise(Snapshot before, Snapshot after, Event source, Instant at) {
        jdbc.update("""
                UPDATE project_monthly_snapshot SET province_id = UUID_TO_BIN(?), city_id = UUID_TO_BIN(?),
                    city_name = ?, industry_code = ?, industry_name = ?, stage_code = ?, total_amount = ?,
                    network_amount = ?, gewu_amount = ?, big_data_amount = ?, wangwu_amount = ?,
                    revision_no = revision_no + 1, updated_at = ?
                WHERE month_start = ? AND project_id = UUID_TO_BIN(?)
                """, after.provinceId().toString(), after.cityId().toString(), after.cityName(),
                after.industryCode(), after.industryName(), after.stageCode(), after.totalAmount(),
                after.categories().network(), after.categories().gewu(),
                after.categories().bigData(), after.categories().wangwu(),
                Timestamp.from(at), date(after.month()), after.projectId().toString());
        jdbc.update("""
                INSERT INTO project_monthly_snapshot_revision
                    (id, month_start, project_id, revision_no, source_kind, source_id, reason,
                     before_stage_code, after_stage_code, before_industry_code, after_industry_code,
                     before_total_amount, after_total_amount, revised_at)
                VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), date(after.month()), after.projectId().toString(),
                before.revisionNo() + 1, source.sourceKind(), source.sourceId().toString(), source.reason(),
                before.stageCode(), after.stageCode(), before.industryCode(), after.industryCode(),
                before.totalAmount(), after.totalAmount(), Timestamp.from(at));
    }

    public List<Group> group(YearMonth month, ProjectDataScope scope, boolean includeTerminated, String dimension) {
        var column = switch (dimension) {
            case "stage" -> "stage_code";
            case "city" -> "BIN_TO_UUID(city_id)";
            case "industry" -> "industry_code";
            default -> throw new IllegalArgumentException("不支持的月末汇总维度");
        };
        var nameColumn = switch (dimension) {
            case "city" -> "city_name";
            case "industry" -> "industry_name";
            default -> "stage_code";
        };
        var scopeSql = switch (scope.level()) {
            case NATIONAL -> "";
            case PROVINCE -> "AND province_id = UUID_TO_BIN(?) ";
            case CITY -> "AND city_id = UUID_TO_BIN(?) ";
        };
        var sql = "SELECT " + column + " AS code, " + nameColumn + " AS display_name, "
                + "COUNT(*) AS project_count, COALESCE(SUM(total_amount), 0) AS amount "
                + "FROM project_monthly_snapshot WHERE month_start = ? " + scopeSql
                + (includeTerminated ? "" : "AND stage_code <> 'TERMINATED' ")
                + "GROUP BY " + column + ", " + nameColumn;
        var arguments = scope.level() == ProjectDataScope.Level.NATIONAL
                ? new Object[] {date(month)} : new Object[] {date(month), scope.organizationId().toString()};
        return jdbc.query(sql, (rs, rowNum) -> new Group(rs.getString("code"),
                rs.getString("display_name"), rs.getLong("project_count"),
                rs.getBigDecimal("amount")), arguments);
    }

    public ProjectCategoryTotals categoryTotals(YearMonth month, ProjectDataScope scope,
            boolean includeTerminated) {
        var scopeSql = switch (scope.level()) {
            case NATIONAL -> "";
            case PROVINCE -> "AND province_id = UUID_TO_BIN(?) ";
            case CITY -> "AND city_id = UUID_TO_BIN(?) ";
        };
        var sql = "SELECT COALESCE(SUM(network_amount), 0) AS network_amount, "
                + "COALESCE(SUM(gewu_amount), 0) AS gewu_amount, "
                + "COALESCE(SUM(big_data_amount), 0) AS big_data_amount, "
                + "COALESCE(SUM(wangwu_amount), 0) AS wangwu_amount "
                + "FROM project_monthly_snapshot WHERE month_start = ? " + scopeSql
                + (includeTerminated ? "" : "AND stage_code <> 'TERMINATED'");
        var arguments = scope.level() == ProjectDataScope.Level.NATIONAL
                ? new Object[] {date(month)} : new Object[] {date(month), scope.organizationId().toString()};
        return jdbc.queryForObject(sql, (rs, rowNum) -> new ProjectCategoryTotals(
                rs.getBigDecimal("network_amount"), rs.getBigDecimal("gewu_amount"),
                rs.getBigDecimal("big_data_amount"), rs.getBigDecimal("wangwu_amount")), arguments);
    }

    public List<StageTrendGroup> stageTrend(YearMonth from, YearMonth to,
            ProjectDataScope scope, List<String> stageCodes) {
        var scopeSql = switch (scope.level()) {
            case NATIONAL -> "";
            case PROVINCE -> "AND province_id = UUID_TO_BIN(:organizationId) ";
            case CITY -> "AND city_id = UUID_TO_BIN(:organizationId) ";
        };
        var sql = """
                SELECT month_start, stage_code, COUNT(*) AS project_count,
                       COALESCE(SUM(total_amount), 0) AS total_amount,
                       COALESCE(SUM(network_amount), 0) AS network_amount,
                       COALESCE(SUM(gewu_amount), 0) AS gewu_amount,
                       COALESCE(SUM(big_data_amount), 0) AS big_data_amount,
                       COALESCE(SUM(wangwu_amount), 0) AS wangwu_amount
                FROM project_monthly_snapshot
                WHERE month_start BETWEEN :fromMonth AND :toMonth
                """ + scopeSql + "AND stage_code IN (:stageCodes) GROUP BY month_start, stage_code";
        var parameters = new MapSqlParameterSource()
                .addValue("fromMonth", date(from)).addValue("toMonth", date(to))
                .addValue("stageCodes", stageCodes);
        if (scope.organizationId() != null) {
            parameters.addValue("organizationId", scope.organizationId().toString());
        }
        return namedJdbc.query(sql, parameters, (rs, rowNum) -> new StageTrendGroup(
                YearMonth.from(rs.getDate("month_start").toLocalDate()),
                rs.getString("stage_code"), rs.getLong("project_count"),
                rs.getBigDecimal("total_amount"), new ProjectCategoryTotals(
                        rs.getBigDecimal("network_amount"), rs.getBigDecimal("gewu_amount"),
                        rs.getBigDecimal("big_data_amount"), rs.getBigDecimal("wangwu_amount"))));
    }

    public List<ProjectMonthlyRevision> revisions(UUID projectId, YearMonth month) {
        return jdbc.query("""
                SELECT revision_no, source_kind, BIN_TO_UUID(source_id) AS source_id, reason,
                       before_stage_code, after_stage_code, before_industry_code, after_industry_code,
                       before_total_amount, after_total_amount, revised_at
                FROM project_monthly_snapshot_revision
                WHERE project_id = UUID_TO_BIN(?) AND month_start = ? ORDER BY revision_no
                """, (rs, rowNum) -> new ProjectMonthlyRevision(month, projectId,
                        rs.getInt("revision_no"), rs.getString("source_kind"),
                        UUID.fromString(rs.getString("source_id")), rs.getString("reason"),
                        rs.getString("before_stage_code"), rs.getString("after_stage_code"),
                        rs.getString("before_industry_code"), rs.getString("after_industry_code"),
                        rs.getBigDecimal("before_total_amount"), rs.getBigDecimal("after_total_amount"),
                        rs.getTimestamp("revised_at").toInstant()), projectId.toString(), date(month));
    }

    private static Date date(YearMonth month) { return Date.valueOf(month.atDay(1)); }
    private static String id(UUID value) { return value == null ? null : value.toString(); }
    private static UUID uuid(String value) { return value == null ? null : UUID.fromString(value); }
    private static ProjectCategoryTotals categories(BigDecimal network, BigDecimal gewu,
            BigDecimal bigData, BigDecimal wangwu) {
        if (network == null) return null;
        return new ProjectCategoryTotals(network, gewu, bigData, wangwu);
    }

    public record Event(UUID projectId, YearMonth month, Instant recordedAt, String sourceKind,
                        UUID sourceId, String reason, boolean baseline, UUID provinceId, UUID cityId,
                        String industryCode, String stageCode, BigDecimal totalAmount,
                        ProjectCategoryTotals categories) { }

    public record Snapshot(UUID projectId, YearMonth month, UUID provinceId, UUID cityId,
                           String cityName, String industryCode, String industryName,
                           String stageCode, BigDecimal totalAmount,
                           ProjectCategoryTotals categories, int revisionNo) { }

    public record Group(String code, String name, long projectCount, BigDecimal amount) { }
    public record StageTrendGroup(YearMonth month, String stageCode, long projectCount,
                                  BigDecimal totalAmount, ProjectCategoryTotals categoryTotals) { }
}
