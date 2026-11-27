package com.pluto.ndi.capabilities;

import com.pluto.ndi.shared.filter.ListFilters;

import static com.pluto.ndi.capabilities.GewuDeploymentService.*;

import com.pluto.ndi.shared.reporting.DashboardSummary;
import com.pluto.ndi.shared.reporting.DashboardSummary.Metric;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.documents.service.*;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.organization.service.*;
import com.pluto.ndi.shared.web.BusinessException;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.*;
import java.util.*;

@Service
@Transactional
public class IntelligenceService {
    private static final String SCREENSHOT = "AGENT_TOKEN_SCREENSHOT";
    private final GewuDeploymentService permissions;
    private final OrganizationDirectory organizations;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DocumentDirectory documents;
    private final Clock clock;
    private final com.pluto.ndi.shared.audit.AuditWriter audit;

    public IntelligenceService(
            GewuDeploymentService permissions,
            OrganizationDirectory organizations,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper json,
            DocumentDirectory documents,
            Clock clock,
            com.pluto.ndi.shared.audit.AuditWriter audit) {
        this.permissions = permissions;
        this.organizations = organizations;
        this.jdbc = jdbc;
        this.json = json;
        this.documents = documents;
        this.clock = clock;
        this.audit = audit;
    }

    private static final String REVENUE_TABLE_FIELD = "是否在计收表中出现";
    private static final List<String> LEGACY_REVENUE_TABLE_FIELDS =
            List.of("是否在子辉的计收表出现", "是否同步到计收表");

    private static String revenueTableValue(Map<String, String> fields) {
        if (fields.containsKey(REVENUE_TABLE_FIELD)) return fields.get(REVENUE_TABLE_FIELD);
        return LEGACY_REVENUE_TABLE_FIELDS.stream().map(fields::get)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    public record Input(UUID provinceId, Map<String, String> fields) {}

    public record Entry(
            UUID id,
            UUID provinceId,
            UUID cityId,
            long version,
            Instant updatedAt,
            Map<String, String> fields) {}

    public record Listing(
            List<Entry> items, long totalElements, int totalPages, int page, int size) {}

    public record Token(UUID id, String month, String amount, UUID screenshotId) {}

    // 台账仅录入省分，地市账号新增时由服务端记录所属地市。
    ProjectDataScope scope(UUID actor) {
        return permissions.scope(actor);
    }

    @Transactional(readOnly = true)
    public List<ProvinceOption> provinces(UUID actor) {
        return permissions.reference(actor).provinces();
    }

    // 无效省分由导入流程记录为行错误。
    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    Input excel(Map<String, String> cells) {
        var province =
                organizations
                        .findEnabledProvinceByName(cells.getOrDefault("省分", ""))
                        .orElseThrow(() -> invalid("省分未匹配启用的行政区划"));
        return new Input(province.id(), cells);
    }

    @Transactional(
            readOnly = true,
            noRollbackFor = BusinessException.class)
    public Input validate(UUID actor, IntelligenceKind kind, Input input) {
        if (input == null || input.provinceId() == null || input.fields() == null)
            throw invalid("请选择省分并填写记录");
        if (provinces(actor).stream().noneMatch(p -> p.id().equals(input.provinceId())))
            throw failure(HttpStatus.FORBIDDEN, "省分超出维护范围或未启用");
        var details = new LinkedHashMap<String, String>();
        for (String field : kind.fields) {
            if (field.equals("省分")) continue;
            var value = Objects.toString(
                    field.equals(REVENUE_TABLE_FIELD) ? revenueTableValue(input.fields()) : input.fields().get(field), "").strip();
            if (value.isEmpty()) {
                if (kind.required.contains(field)) throw invalid(field + "不能为空");
                continue;
            }
            if (value.length() > 4000) throw invalid(field + "超过字数限制");
            if (field.equals("环节")) value = value.replaceFirst("^[1-4]-", "");
            if (IntelligenceKind.OPTIONS.containsKey(field)
                    && !IntelligenceKind.OPTIONS.get(field).contains(value))
                throw invalid(field + "选项无效");
            if (IntelligenceKind.MONTHS.contains(field)) value = month(value, field);
            if (field.equals("版本更新日期")) {
                try {
                    value =
                            LocalDate.parse(
                                            value.replace('/', '-').replace('.', '-'),
                                            DateTimeFormatter.ofPattern("uuuu-M-d")
                                                    .withResolverStyle(ResolverStyle.STRICT))
                                    .toString();
                    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000"))
                        throw invalid(field + "无效");
                } catch (DateTimeException e) {
                    throw invalid(field + "须为有效日期");
                }
            }
            if (field.equals("开发进度")) {
                var number = decimal(value.replaceFirst("%$", "").strip(), field);
                if (number.compareTo(BigDecimal.valueOf(100)) > 0)
                    throw invalid("开发进度须在 0–100% 范围内");
                value = number.toPlainString();
            }
            details.put(field, value);
        }
        return new Input(input.provinceId(), details);
    }

    private static String month(String value, String field) {
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}") || value.startsWith("0000"))
                throw invalid(field + "须填写年份和月份（YYYY-MM）");
            return YearMonth.parse(value).toString();
        } catch (DateTimeException e) {
            throw invalid(field + "须为有效月份（YYYY-MM）");
        }
    }

    static BigDecimal decimal(String value, String field) {
        if (value == null || !value.matches("[0-9]{1,16}(\\.[0-9]{1,4})?"))
            throw invalid(field + "须为非负数，最多四位小数");
        return new BigDecimal(value).stripTrailingZeros();
    }

    private static final String SELECT =
            """
SELECT BIN_TO_UUID(e.id) id,BIN_TO_UUID(e.province_id) province,BIN_TO_UUID(e.city_id) city,
       e.version_no,e.updated_at,e.version_updated_date,e.details,p.province_name
  FROM intelligence_capability e JOIN organization_province p ON p.id=e.province_id
""";

    @Transactional(readOnly = true)
    public DashboardSummary dashboard(UUID actor, IntelligenceKind kind) {
        var args = new MapSqlParameterSource();
        return DashboardSummary.quantities(jdbc.query("""
                SELECT p.province_name, COUNT(*) AS records
                FROM intelligence_capability e JOIN organization_province p ON p.id=e.province_id
                """ + restriction(actor, kind, args) + " GROUP BY p.province_name ORDER BY records DESC, p.province_name",
                args, (rs, row) -> new Metric(rs.getString("province_name"), rs.getLong("records"), (String) null)));
    }

    private String restriction(UUID actor, IntelligenceKind kind, MapSqlParameterSource args) {
        args.addValue("kind", kind.name());
        var scope = scope(actor);
        return " WHERE e.kind=:kind AND e.active_key=1"
                + switch (scope.level()) {
                    case NATIONAL -> "";
                    case CITY -> {
                        args.addValue("scopeCity", scope.organizationId().toString());
                        yield " AND e.city_id=UUID_TO_BIN(:scopeCity)";
                    }
                    case PROVINCE -> {
                        args.addValue(
                                "scopeProvinces",
                                scope.provinceIds().stream().map(UUID::toString).toList());
                        yield " AND BIN_TO_UUID(e.province_id) IN (:scopeProvinces)";
                    }
                };
    }

    private Entry row(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        try {
            Map<String, String> fields =
                    json.readValue(rs.getString("details"), new TypeReference<>() {});
            var revenueStatus = revenueTableValue(fields);
            LEGACY_REVENUE_TABLE_FIELDS.forEach(fields::remove);
            if (revenueStatus != null) fields.put(REVENUE_TABLE_FIELD, revenueStatus);
            fields.put("省分", rs.getString("province_name"));
            if (rs.getDate("version_updated_date") != null)
                fields.put("版本更新日期", rs.getDate("version_updated_date").toLocalDate().toString());
            var city = rs.getString("city");
            return new Entry(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("province")),
                    city == null ? null : UUID.fromString(city),
                    rs.getLong("version_no"),
                    rs.getTimestamp("updated_at").toInstant(),
                    fields);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public void delete(UUID actor, IntelligenceKind kind, UUID id, long version) {
        var entry = detail(actor, kind, id, true);
        if (entry.version() != version) throw failure(HttpStatus.CONFLICT, "记录已变更，请刷新后重试");
        jdbc.update(
                "UPDATE intelligence_capability SET deleted_at=:at,updated_at=:at,version_no=version_no+1"
                        + " WHERE id=UUID_TO_BIN(:id)",
                Map.of("at", Timestamp.from(clock.instant()), "id", id.toString()));
        audit.record(actor, kind.name(), id, "INTELLIGENCE_CAPABILITY_DELETED", Map.of("version", version));
    }

    @Transactional(readOnly = true)
    public Listing list(
            UUID actor,
            IntelligenceKind kind,
            List<UUID> provinces,
            String reviewer,
            String place,
            List<String> progress,
            int page,
            int size) {
        if (page < 0
                || page > 100000
                || size < 1
                || size > 100
                || reviewer.length() > 300
                || place.length() > 4000
               ) throw invalid("筛选或分页参数无效");
        var args = new MapSqlParameterSource();
        var where = restriction(actor, kind, args);
        if (!provinces.isEmpty()) {
            where += " AND BIN_TO_UUID(e.province_id) IN (:provinces)";
            args.addValue("provinces", provinces.stream().map(UUID::toString).toList());
        }
        if (!reviewer.isBlank()) {
            where +=
                    " AND JSON_UNQUOTE(JSON_EXTRACT(e.details,'$.\"接口人\"')) LIKE :reviewer ESCAPE"
                        + " '!'";
            args.addValue(
                    "reviewer",
                    "%"
                            + reviewer.strip()
                                    .replace("!", "!!")
                                    .replace("%", "!%")
                                    .replace("_", "!_")
                            + "%");
        }
        if (kind == IntelligenceKind.WANGWU && !place.isBlank()) {
            where += " AND JSON_UNQUOTE(JSON_EXTRACT(e.details,'$.\"部署地点\"'))=:place";
            args.addValue("place", place.strip());
        }
        var progressValues = ListFilters.choices(progress, kind == IntelligenceKind.WANGWU
                ? IntelligenceKind.OPTIONS.get("部署进度") : List.of());
        if (!progressValues.isEmpty()) {
            where += " AND JSON_UNQUOTE(JSON_EXTRACT(e.details,'$.\"部署进度\"')) IN (:progress)";
            args.addValue("progress", progressValues);
        }
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM intelligence_capability e" + where, args, Long.class);
        var items =
                jdbc.query(
                        SELECT
                                + where
                                + " ORDER BY e.updated_at DESC,e.id LIMIT :size OFFSET :offset",
                        args.addValue("size", size).addValue("offset", (long) page * size),
                        this::row);
        return new Listing(items, count, (int) ((count + size - 1) / size), page, size);
    }

    public Entry detail(UUID actor, IntelligenceKind kind, UUID id, boolean lock) {
        var args = new MapSqlParameterSource("id", id.toString());
        return jdbc
                .query(
                        SELECT
                                + restriction(actor, kind, args)
                                + " AND e.id=UUID_TO_BIN(:id)"
                                + (lock ? " FOR UPDATE" : ""),
                        args,
                        this::row)
                .stream()
                .findFirst()
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "记录不存在或超出维护范围"));
    }

    public Entry save(UUID actor, IntelligenceKind kind, UUID id, Long version, Input input) {
        var old = id == null ? null : detail(actor, kind, id, true);
        if (old != null && !Objects.equals(old.version(), version))
            throw failure(HttpStatus.CONFLICT, "记录已更新，请重新加载");
        var value = validate(actor, kind, input);
        var scope = scope(actor);
        UUID city =
                scope.level() == ProjectDataScope.Level.CITY
                        ? scope.organizationId()
                        : old != null && old.provinceId().equals(value.provinceId())
                                ? old.cityId()
                                : null;
        var key = id == null ? UUID.randomUUID() : id;
        var args =
                new MapSqlParameterSource("id", key.toString())
                        .addValue("kind", kind.name())
                        .addValue("province", value.provinceId().toString())
                        .addValue("city", city == null ? null : city.toString())
                        .addValue("at", Timestamp.from(clock.instant()));
        var details = new LinkedHashMap<>(value.fields());
        var date = details.remove("版本更新日期");
        args.addValue("date", date == null ? null : java.sql.Date.valueOf(date));
        try {
            args.addValue("details", json.writeValueAsString(details));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        CapabilityBusinessKey.save(duplicate(kind, value, id), () -> {
            if (old == null)
                jdbc.update(
                        """
INSERT INTO intelligence_capability(id,kind,province_id,city_id,version_updated_date,details,updated_at)
VALUES(UUID_TO_BIN(:id),:kind,UUID_TO_BIN(:province),UUID_TO_BIN(:city),:date,CAST(:details AS JSON),:at)
""",
                        args);
            else
                jdbc.update(
                        """
UPDATE intelligence_capability SET province_id=UUID_TO_BIN(:province),city_id=UUID_TO_BIN(:city),
version_updated_date=:date,details=CAST(:details AS JSON),updated_at=:at,version_no=version_no+1 WHERE id=UUID_TO_BIN(:id)
""",
                        args);
        });
        return detail(actor, kind, key, false);
    }

    boolean duplicate(IntelligenceKind kind, Input input, UUID exceptId) {
        return CapabilityBusinessKey.exists(jdbc, kind, CapabilityBusinessKey.intelligence(kind, input), exceptId);
    }

    public List<Token> tokens(UUID actor, UUID agent) {
        detail(actor, IntelligenceKind.AGENTS_LIVE, agent, false);
        return tokens(agent, false);
    }

    private List<Token> tokens(UUID agent, boolean lock) {
        return jdbc.query(
                """
SELECT BIN_TO_UUID(id),metric_month,amount,BIN_TO_UUID(screenshot_id)
FROM intelligence_token WHERE agent_id=UUID_TO_BIN(:agent) ORDER BY metric_month DESC
""" + (lock ? " FOR UPDATE" : ""),
                Map.of("agent", agent.toString()),
                (rs, i) ->
                        new Token(
                                UUID.fromString(rs.getString(1)),
                                rs.getString(2),
                                rs.getBigDecimal(3).stripTrailingZeros().toPlainString(),
                                rs.getString(4) == null ? null : UUID.fromString(rs.getString(4))));
    }

    @Transactional(readOnly = true)
    public List<Entry> matchTokenAgents(UUID actor, String province, String name, String project, String systemId) {
        var args = new MapSqlParameterSource();
        var where = restriction(actor, IntelligenceKind.AGENTS_LIVE, args);
        if (!systemId.isBlank()) {
            try {
                args.addValue("agent", UUID.fromString(systemId).toString());
            } catch (IllegalArgumentException e) {
                return List.of();
            }
            where += " AND e.id=UUID_TO_BIN(:agent)";
        } else {
            if (name.isBlank()) return List.of();
            var location = organizations.findEnabledProvinceByName(province);
            if (location.isEmpty()) return List.of();
            args.addValue("province", location.get().id().toString()).addValue("name", name);
            where += " AND e.province_id=UUID_TO_BIN(:province)"
                    + " AND CAST(JSON_UNQUOTE(JSON_EXTRACT(e.details,'$.\"智能体名称\"')) AS BINARY)=CAST(:name AS BINARY)";
            if (!project.isBlank()) {
                args.addValue("project", project);
                where += " AND CAST(JSON_UNQUOTE(JSON_EXTRACT(e.details,'$.\"应用项目名称\"')) AS BINARY)"
                        + " = CAST(:project AS BINARY)";
            }
        }
        return jdbc.query(SELECT + where + " LIMIT 2", args, this::row);
    }

    record ImportedToken(String month, String amount, UUID expected, String imageType, byte[] image) {}

    void importTokens(UUID actor, UUID agent, List<ImportedToken> rows) {
        detail(actor, IntelligenceKind.AGENTS_LIVE, agent, true);
        var history = new HashMap<String, Token>();
        tokens(agent, true).forEach(token -> history.put(token.month(), token));
        int increments = 0;
        for (var row : rows) {
            var old = history.get(row.month());
            var amount = decimal(row.amount(), "Token 量");
            if (old != null && (!old.id().equals(row.expected()) || old.screenshotId() != null
                    || row.image() == null || decimal(old.amount(), "Token 量").compareTo(amount) != 0)
                    || old == null && row.expected() != null)
                throw failure(HttpStatus.CONFLICT, "月度记录已变化，请重新预检");

            var id = old == null ? UUID.randomUUID() : old.id();
            if (old == null) {
                try {
                    jdbc.update("""
                            INSERT INTO intelligence_token(id,agent_id,metric_month,amount)
                            VALUES(UUID_TO_BIN(:id),UUID_TO_BIN(:agent),:month,:amount)
                            """, Map.of("id", id.toString(), "agent", agent.toString(), "month", row.month(), "amount", amount));
                } catch (org.springframework.dao.DuplicateKeyException conflict) {
                    throw failure(HttpStatus.CONFLICT, "月度记录已变化，请重新预检");
                }
                increments++;
            }
            if (row.image() != null) {
                var document = documents.storeImage(SCREENSHOT, id, actor, row.imageType(), row.image());
                jdbc.update("UPDATE intelligence_token SET screenshot_id=UUID_TO_BIN(:document) WHERE id=UUID_TO_BIN(:id)",
                        Map.of("document", document.toString(), "id", id.toString()));
                increments++;
            }
        }
        touch(agent, increments);
    }

    public UUID saveToken(UUID actor, UUID agent, UUID id, String month, String amount) {
        detail(actor, IntelligenceKind.AGENTS_LIVE, agent, true);
        String normalized = month(Objects.toString(month, ""), "统计月份");
        var number = decimal(amount, "Token 量");
        var key = id == null ? UUID.randomUUID() : id;
        if (id != null) requireToken(actor, agent, id);
        if (tokens(actor, agent).stream()
                .anyMatch(t -> t.month().equals(normalized) && !t.id().equals(key)))
            throw failure(HttpStatus.CONFLICT, "该智能体已经登记此月份");
        var args =
                Map.of(
                        "id",
                        key.toString(),
                        "agent",
                        agent.toString(),
                        "month",
                        normalized,
                        "amount",
                        number);
        if (id == null)
            jdbc.update(
                    "INSERT INTO intelligence_token(id,agent_id,metric_month,amount)"
                        + " VALUES(UUID_TO_BIN(:id),UUID_TO_BIN(:agent),:month,:amount)",
                    args);
        else
            jdbc.update(
                    "UPDATE intelligence_token SET metric_month=:month,amount=:amount WHERE"
                        + " id=UUID_TO_BIN(:id)",
                    args);
        touch(agent);
        return key;
    }

    private Token requireToken(UUID actor, UUID agent, UUID token) {
        return tokens(actor, agent).stream()
                .filter(t -> t.id().equals(token))
                .findFirst()
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "月度记录不存在"));
    }

    public UploadIntent upload(UUID actor, UUID agent, UUID token, UploadCommand input) {
        requireToken(actor, agent, token);
        if (!Set.of("image/png", "image/jpeg").contains(Objects.toString(input.mediaType(), "")))
            throw invalid("请选择 PNG 或 JPEG 截图");
        return documents.beginUpload(
                SCREENSHOT,
                token,
                actor,
                new UploadCommand(
                        SCREENSHOT,
                        input.originalName(),
                        input.mediaType(),
                        input.byteSize(),
                        input.sha256Hex()));
    }

    public void complete(UUID actor, UUID agent, UUID token, UUID document) {
        detail(actor, IntelligenceKind.AGENTS_LIVE, agent, true);
        var old = requireToken(actor, agent, token);
        documents.completeUpload(SCREENSHOT, token, actor, document);
        if (document.equals(old.screenshotId())) return;
        jdbc.update(
                "UPDATE intelligence_token SET screenshot_id=UUID_TO_BIN(:document) WHERE"
                    + " id=UUID_TO_BIN(:token)",
                Map.of("document", document.toString(), "token", token.toString()));
        if (old.screenshotId() != null) documents.delete(SCREENSHOT, token, old.screenshotId());
        touch(agent);
    }

    public DownloadIntent download(UUID actor, UUID agent, UUID token) {
        var value = requireToken(actor, agent, token);
        if (value.screenshotId() == null) throw failure(HttpStatus.NOT_FOUND, "截图不存在");
        return documents.download(SCREENSHOT, token, value.screenshotId());
    }

    private void touch(UUID agent) {
        touch(agent, 1);
    }

    private void touch(UUID agent, int increments) {
        jdbc.update(
                "UPDATE intelligence_capability SET updated_at=:at,version_no=version_no+:increments WHERE"
                    + " id=UUID_TO_BIN(:id)",
                Map.of("at", Timestamp.from(clock.instant()), "id", agent.toString(), "increments", increments));
    }
}
