package com.pluto.ndi.capabilities;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.organization.service.*;
import com.pluto.ndi.shared.audit.AuditWriter;
import com.pluto.ndi.shared.web.BusinessException;
import com.pluto.ndi.shared.web.ErrorCode;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class GewuDeploymentService {
    static final List<String> REQUIRED =
            List.of(
                    "接口人",
                    "省份",
                    "地市",
                    "客户名称",
                    "项目名称",
                    "制造类型",
                    "国民经济行业分类",
                    "所属行业",
                    "部署版本",
                    "上一周期是否已接设备");
    static final List<String> FIELDS =
            java.util.stream.Stream.concat(
                            REQUIRED.stream(),
                            List.of(
                                    "物模型总数",
                                    "接入设备总数量",
                                    "协议数量",
                                    "日均上报数据量",
                                    "设备接入日期",
                                    "是否最新工具包",
                                    "是否可升级工具包",
                                    "备注")
                                    .stream())
                    .toList();
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT);
    private static final String SELECT =
            """
            SELECT BIN_TO_UUID(d.id) id, BIN_TO_UUID(d.province_id) province,
                BIN_TO_UUID(d.city_id) city, d.version_no, d.details,
                p.province_name, c.city_name
            FROM capability_gewu_deployment d
            JOIN organization_province p ON p.id=d.province_id
            JOIN organization_city c ON c.id=d.city_id
            """;

    public record Input(UUID provinceId, UUID cityId, Map<String, String> fields) {}

    public record Deployment(
            UUID id, long version, UUID provinceId, UUID cityId, Map<String, String> fields) {}

    public record Reference(List<ProvinceOption> provinces, List<CityOption> cities) {}

    public record Listing(
            List<Deployment> items, long totalElements, int totalPages, int page, int size) {}

    private final IdentityDirectory identities;
    private final OrganizationDirectory organizations;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditWriter audit;
    private final Clock clock;

    public GewuDeploymentService(
            IdentityDirectory identities,
            OrganizationDirectory organizations,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper json,
            AuditWriter audit,
            Clock clock) {
        this.identities = identities;
        this.organizations = organizations;
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.clock = clock;
    }

    ProjectDataScope scope(UUID actor) {
        return identities
                .findProjectActor(actor)
                .flatMap(identities::findReadableProjectScope)
                .orElseThrow(() -> failure(HttpStatus.FORBIDDEN, "无格物部署维护权限"));
    }

    private boolean contains(ProjectDataScope scope, UUID province, UUID city) {
        return switch (scope.level()) {
            case NATIONAL -> true;
            case PROVINCE -> scope.provinceIds().contains(province);
            case CITY -> scope.organizationId().equals(city);
        };
    }

    @Transactional(readOnly = true)
    public Reference reference(UUID actor) {
        var scope = scope(actor);
        var cities =
                organizations.findEnabledCities().stream()
                        .filter(city -> contains(scope, city.provinceId(), city.id()))
                        .toList();
        var provinces = cities.stream().map(CityOption::provinceId).collect(Collectors.toSet());
        return new Reference(
                organizations.findEnabledProvinces().stream()
                        .filter(province -> provinces.contains(province.id()))
                        .toList(),
                cities);
    }

    @Transactional(readOnly = true)
    public Listing list(
            UUID actor,
            UUID province,
            UUID city,
            String search,
            String version,
            String connected,
            int page,
            int size) {
        if (page < 0
                || page > 100000
                || size < 1
                || size > 100
                || search.length() > 300
                || version.length() > 100
                || connected.length() > 10) throw invalid("筛选或分页参数无效");
        var scope = scope(actor);
        var args = new MapSqlParameterSource();
        var where = new StringBuilder(" WHERE d.deleted_at IS NULL");
        switch (scope.level()) {
            case CITY -> {
                where.append(" AND d.city_id=UUID_TO_BIN(:scopeCity)");
                args.addValue("scopeCity", scope.organizationId().toString());
            }
            case PROVINCE -> {
                where.append(" AND BIN_TO_UUID(d.province_id) IN (:scopeProvinces)");
                args.addValue(
                        "scopeProvinces",
                        scope.provinceIds().stream().map(UUID::toString).toList());
            }
            case NATIONAL -> {}
        }
        if (province != null) {
            where.append(" AND d.province_id=UUID_TO_BIN(:province)");
            args.addValue("province", province.toString());
        }
        if (city != null) {
            where.append(" AND d.city_id=UUID_TO_BIN(:city)");
            args.addValue("city", city.toString());
        }
        if (!search.isBlank()) {
            where.append(
                    """
                     AND (JSON_UNQUOTE(JSON_EXTRACT(d.details,'$."项目名称"')) LIKE :search ESCAPE '!'
                        OR JSON_UNQUOTE(JSON_EXTRACT(d.details,'$."客户名称"')) LIKE :search ESCAPE '!'
                        OR JSON_UNQUOTE(JSON_EXTRACT(d.details,'$."接口人"')) LIKE :search ESCAPE '!')
                    """);
            args.addValue(
                    "search",
                    "%"
                            + search.strip()
                                    .replace("!", "!!")
                                    .replace("%", "!%")
                                    .replace("_", "!_")
                            + "%");
        }
        if (!version.isBlank()) {
            where.append(" AND JSON_UNQUOTE(JSON_EXTRACT(d.details,'$.\"部署版本\"'))=:version");
            args.addValue("version", version.strip());
        }
        if (!connected.isBlank()) {
            where.append(
                    " AND JSON_UNQUOTE(JSON_EXTRACT(d.details,'$.\"上一周期是否已接设备\"'))=:connected");
            args.addValue("connected", connected);
        }
        long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM capability_gewu_deployment d" + where,
                        args,
                        Long.class);
        args.addValue("size", size).addValue("offset", (long) page * size);
        var items =
                jdbc.query(
                        SELECT
                                + where
                                + " ORDER BY d.updated_at DESC, d.id LIMIT :size OFFSET :offset",
                        args,
                        row());
        return new Listing(items, count, (int) ((count + size - 1) / size), page, size);
    }

    Input fromExcel(Map<String, String> fields) {
        var location =
                organizations
                        .findEnabledLocationByNames(fields.get("省份"), fields.get("地市"))
                        .orElseThrow(() -> invalid("省份和地市须匹配启用的组织"));
        return new Input(location.provinceId(), location.cityId(), fields);
    }

    @Transactional(readOnly = true, noRollbackFor = BusinessException.class)
    public Input validate(UUID actor, Input input) {
        if (input == null
                || input.provinceId() == null
                || input.cityId() == null
                || input.fields() == null) throw invalid("请选择省份和地市并填写部署信息");
        requireScope(actor, input.provinceId(), input.cityId());
        if (organizations.findEnabledLocation(input.provinceId(), input.cityId()).isEmpty())
            throw invalid("省份和地市须匹配启用的组织");
        var fields = new LinkedHashMap<String, String>();
        for (String field : FIELDS) {
            if (field.equals("省份") || field.equals("地市")) continue;
            String value = Objects.toString(input.fields().get(field), "").strip();
            if (REQUIRED.contains(field) && value.isEmpty()) throw invalid(field + "不能为空");
            if (value.length() > (field.equals("备注") ? 2000 : 300)) throw invalid(field + "超出字数限制");
            if (!value.isEmpty()) fields.put(field, value);
        }
        for (String field : List.of("物模型总数", "接入设备总数量", "协议数量", "日均上报数据量")) {
            String value = fields.get(field);
            if (value == null) continue;
            try {
                long number = Long.parseLong(value.replace(",", ""));
                if (number < 0) throw new NumberFormatException();
                fields.put(field, Long.toString(number));
            } catch (NumberFormatException failure) {
                throw invalid(field + "须为非负整数");
            }
        }
        for (String field : List.of("上一周期是否已接设备", "是否最新工具包", "是否可升级工具包")) {
            String value = fields.get(field);
            if (value != null
                    && !Set.of("是", "否").contains(value)
                    && !(value.equals("不涉及") && !field.equals("上一周期是否已接设备")))
                throw invalid(field + "取值无效");
        }
        if (fields.containsKey("设备接入日期")) {
            try {
                fields.put(
                        "设备接入日期",
                        LocalDate.parse(fields.get("设备接入日期").replace('/', '-'), DATE).toString());
            } catch (DateTimeException failure) {
                throw invalid("设备接入日期须为有效日期");
            }
        }
        return new Input(input.provinceId(), input.cityId(), fields);
    }

    public Deployment create(UUID actor, Input input) {
        var value = validate(actor, input);
        var id = UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO capability_gewu_deployment(id,province_id,city_id,details,updated_at)
VALUES(UUID_TO_BIN(:id),UUID_TO_BIN(:province),UUID_TO_BIN(:city),CAST(:fields AS JSON),:at)
""",
                parameters(id, value));
        audit.record(actor, "GEWU_DEPLOYMENT", id, "GEWU_DEPLOYMENT_CREATED", Map.of());
        return get(id, false);
    }

    public Deployment update(UUID actor, UUID id, long version, Input input) {
        lock(actor, id, version);
        var value = validate(actor, input);
        jdbc.update(
                """
                UPDATE capability_gewu_deployment SET province_id=UUID_TO_BIN(:province),
                    city_id=UUID_TO_BIN(:city),details=CAST(:fields AS JSON),
                    version_no=version_no+1,updated_at=:at WHERE id=UUID_TO_BIN(:id)
                """,
                parameters(id, value));
        audit.record(
                actor,
                "GEWU_DEPLOYMENT",
                id,
                "GEWU_DEPLOYMENT_UPDATED",
                Map.of("version", version));
        return get(id, false);
    }

    public void delete(UUID actor, UUID id, long version) {
        lock(actor, id, version);
        jdbc.update(
                "UPDATE capability_gewu_deployment SET deleted_at=:at WHERE id=UUID_TO_BIN(:id)",
                Map.of("at", Timestamp.from(Instant.now(clock)), "id", id.toString()));
        audit.record(
                actor,
                "GEWU_DEPLOYMENT",
                id,
                "GEWU_DEPLOYMENT_DELETED",
                Map.of("version", version));
    }

    private Deployment get(UUID id, boolean lock) {
        return jdbc
                .query(
                        SELECT
                                + " WHERE d.id=UUID_TO_BIN(:id) AND d.deleted_at IS NULL"
                                + (lock ? " FOR UPDATE" : ""),
                        Map.of("id", id.toString()),
                        row())
                .stream()
                .findFirst()
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "部署记录不存在"));
    }

    private void lock(UUID actor, UUID id, long version) {
        var item = get(id, true);
        requireScope(actor, item.provinceId(), item.cityId());
        if (item.version() != version) throw failure(HttpStatus.CONFLICT, "记录已变更，请刷新后重试");
    }

    private void requireScope(UUID actor, UUID province, UUID city) {
        if (!contains(scope(actor), province, city))
            throw failure(HttpStatus.FORBIDDEN, "记录超出账号维护范围");
    }

    private MapSqlParameterSource parameters(UUID id, Input input) {
        return new MapSqlParameterSource("id", id.toString())
                .addValue("province", input.provinceId().toString())
                .addValue("city", input.cityId().toString())
                .addValue("fields", encode(input.fields()))
                .addValue("at", Timestamp.from(Instant.now(clock)));
    }

    private RowMapper<Deployment> row() {
        return (rs, index) -> {
            try {
                Map<String, String> fields =
                        json.readValue(rs.getString("details"), new TypeReference<>() {});
                fields.put("省份", rs.getString("province_name"));
                fields.put("地市", rs.getString("city_name"));
                return new Deployment(
                        UUID.fromString(rs.getString("id")),
                        rs.getLong("version_no"),
                        UUID.fromString(rs.getString("province")),
                        UUID.fromString(rs.getString("city")),
                        fields);
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                throw new IllegalStateException("格物部署记录无法读取", failure);
            }
        };
    }

    private String encode(Map<String, String> fields) {
        try {
            return json.writeValueAsString(fields);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("格物部署记录无法保存", failure);
        }
    }

    static BusinessException invalid(String text) {
        return failure(HttpStatus.BAD_REQUEST, text);
    }

    static BusinessException failure(HttpStatus status, String text) {
        return new BusinessException(
                new ErrorCode() {
                    public HttpStatus status() {
                        return status;
                    }

                    public String code() {
                        return "GEWU_DEPLOYMENT_" + status.name();
                    }

                    public String title() {
                        return text;
                    }

                    public String message() {
                        return text;
                    }
                });
    }
}
