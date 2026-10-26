package com.pluto.ndi.identity.mapper;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/** 接口人负责范围的批量读写；写入由账号管理事务统一锁定和审计。 */
@Repository
public class ReviewerProvinceMapper {
    private final NamedParameterJdbcTemplate jdbc;

    public ReviewerProvinceMapper(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, List<UUID>> assignments(Collection<UUID> reviewers) {
        if (reviewers.isEmpty()) return Map.of();
        var result = new HashMap<UUID, List<UUID>>();
        jdbc.query(
                """
SELECT BIN_TO_UUID(mapping.reviewer_id) reviewer_id, BIN_TO_UUID(mapping.province_id) province_id
FROM identity_reviewer_province mapping
JOIN organization_province province ON province.id = mapping.province_id AND province.enabled = TRUE
WHERE mapping.reviewer_id IN (:ids)
ORDER BY mapping.reviewer_id, mapping.province_id
""",
                new MapSqlParameterSource(
                        "ids", reviewers.stream().map(ReviewerProvinceMapper::bytes).toList()),
                rs -> {
                    result.computeIfAbsent(
                                    UUID.fromString(rs.getString("reviewer_id")),
                                    ignored -> new ArrayList<>())
                            .add(UUID.fromString(rs.getString("province_id")));
                });
        result.replaceAll((ignored, values) -> List.copyOf(values));
        return result;
    }

    public List<UUID> provinces(UUID reviewer) {
        return assignments(List.of(reviewer)).getOrDefault(reviewer, List.of());
    }

    public List<UUID> reviewers(UUID province) {
        return jdbc.query(
                """
SELECT BIN_TO_UUID(mapping.reviewer_id) reviewer_id FROM identity_reviewer_province mapping
JOIN organization_province province ON province.id = mapping.province_id AND province.enabled = TRUE
WHERE mapping.province_id = UUID_TO_BIN(:province)
ORDER BY mapping.reviewer_id
""",
                Map.of("province", province.toString()),
                (rs, row) -> UUID.fromString(rs.getString("reviewer_id")));
    }

    public void replace(UUID reviewer, Collection<UUID> provinces) {
        jdbc.update(
                "DELETE FROM identity_reviewer_province WHERE reviewer_id = UUID_TO_BIN(:reviewer)",
                Map.of("reviewer", reviewer.toString()));
        if (provinces.isEmpty()) return;
        var batch =
                provinces.stream()
                        .sorted()
                        .map(
                                province ->
                                        new MapSqlParameterSource("reviewer", reviewer.toString())
                                                .addValue("province", province.toString()))
                        .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(
                "INSERT INTO identity_reviewer_province (reviewer_id, province_id) VALUES"
                        + " (UUID_TO_BIN(:reviewer), UUID_TO_BIN(:province))",
                batch);
    }

    public record Owner(
            UUID provinceId, UUID reviewerId, String displayName, String loginName, long version) {}

    public List<Owner> owners() {
        return jdbc.query(
                """
SELECT BIN_TO_UUID(m.province_id) province, BIN_TO_UUID(u.id) reviewer,
       u.display_name, u.login_name, u.management_version
FROM identity_reviewer_province m JOIN identity_user_account u ON u.id=m.reviewer_id
ORDER BY m.province_id
""",
                Map.of(),
                (rs, row) ->
                        new Owner(
                                UUID.fromString(rs.getString("province")),
                                UUID.fromString(rs.getString("reviewer")),
                                rs.getString("display_name"),
                                rs.getString("login_name"),
                                rs.getLong("management_version")));
    }

    private static byte[] bytes(UUID id) {
        return java.nio.ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }
}
