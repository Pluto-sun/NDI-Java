package com.pluto.ndi.revenue.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RevenueOrganizationMapper {
    public record Assignment(String key, String name, UUID provinceId) {}

    private final JdbcTemplate jdbc;

    public RevenueOrganizationMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Assignment> list() {
        return jdbc.query("""
                SELECT HEX(e.source_organization_key) AS organization_key,
                       MIN(JSON_UNQUOTE(JSON_EXTRACT(e.source_fields, '$."地市/组织名称"'))) AS organization_name,
                       BIN_TO_UUID(m.province_id) AS province
                FROM revenue_entry e
                LEFT JOIN revenue_organization_province m ON m.organization_key=e.source_organization_key
                WHERE e.active_key=1 AND e.source_organization_key IS NOT NULL
                GROUP BY e.source_organization_key, m.province_id
                ORDER BY organization_name, organization_key
                """, (row, index) -> assignment(row));
    }

    public String name(String key) {
        return jdbc.query("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(source_fields, '$."地市/组织名称"'))
                FROM revenue_entry WHERE active_key=1 AND source_organization_key=?
                ORDER BY id LIMIT 1
                """, (row, index) -> row.getString(1), bytes(key)).stream().findFirst().orElse(null);
    }

    public UUID lock(String key) {
        var value = bytes(key);
        jdbc.update("""
                INSERT INTO revenue_organization_province(organization_key) VALUES (?)
                ON DUPLICATE KEY UPDATE organization_key=organization_key
                """, value);
        return jdbc.queryForObject("""
                SELECT BIN_TO_UUID(province_id) FROM revenue_organization_province
                WHERE organization_key=? FOR UPDATE
                """, (row, index) -> uuid(row.getString(1)), value);
    }

    public void update(String key, UUID actor, UUID province, Instant time) {
        jdbc.update("""
                UPDATE revenue_organization_province SET province_id=UUID_TO_BIN(?),
                  updated_by=UUID_TO_BIN(?), updated_at=? WHERE organization_key=?
                """, province == null ? null : province.toString(), actor.toString(), Timestamp.from(time), bytes(key));
    }

    private static Assignment assignment(ResultSet row) throws SQLException {
        return new Assignment(row.getString("organization_key"), row.getString("organization_name"),
                uuid(row.getString("province")));
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static byte[] bytes(String key) {
        return HexFormat.of().parseHex(key);
    }
}
