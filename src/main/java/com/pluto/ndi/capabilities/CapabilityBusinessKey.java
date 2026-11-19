package com.pluto.ndi.capabilities;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.pluto.ndi.capabilities.GewuDeploymentService.failure;

/** 名称去除首尾空白后精确比较。键的编码与迁移 V55 一致。 */
final class CapabilityBusinessKey {
    private CapabilityBusinessKey() {}

    static String gewu(GewuDeploymentService.Input input) {
        return field(input.fields(), "项目名称");
    }

    static String intelligence(IntelligenceKind kind, IntelligenceService.Input input) {
        if (kind == IntelligenceKind.WANGWU) return field(input.fields(), "具体名称");
        var name = field(input.fields(), "智能体名称");
        var project = field(input.fields(), "应用项目名称");
        if (name == null || project == null) return null;
        var key = name.getBytes(StandardCharsets.UTF_8).length + ":" + name + project;
        return kind == IntelligenceKind.AGENTS_LIVE
                ? input.provinceId().toString().replace("-", "").toUpperCase(Locale.ROOT) + key
                : key;
    }

    private static String field(Map<String, String> fields, String field) {
        var text = fields.get(field);
        return text == null || text.isBlank() ? null : text.strip();
    }

    static boolean exists(NamedParameterJdbcTemplate jdbc, IntelligenceKind kind, String key, UUID exceptId) {
        if (key == null) return false;
        var source = kind == null ? "capability_gewu_deployment" : "intelligence_capability";
        var sql = "SELECT EXISTS(SELECT 1 FROM " + source + " WHERE business_key=UNHEX(SHA2(:key,256))";
        var args = new MapSqlParameterSource("key", key);
        if (kind != null) {
            sql += " AND kind=:kind AND active_key=1";
            args.addValue("kind", kind.name());
        }
        if (exceptId != null) {
            sql += " AND id<>UUID_TO_BIN(:id)";
            args.addValue("id", exceptId.toString());
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(sql + ")", args, Boolean.class));
    }

    static void save(boolean duplicate, Runnable write) {
        if (duplicate) throw failure(HttpStatus.CONFLICT, "系统内已存在相同业务唯一键的记录");
        try {
            write.run();
        } catch (DuplicateKeyException e) {
            throw failure(HttpStatus.CONFLICT, "系统内已存在相同业务唯一键的记录");
        }
    }
}
