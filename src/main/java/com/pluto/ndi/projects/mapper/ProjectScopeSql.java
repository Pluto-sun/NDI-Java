package com.pluto.ndi.projects.mapper;

import java.util.*;
import com.pluto.ndi.identity.service.ProjectDataScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** SQL 聚合与明细查询共用同一省市范围，空范围不得退化为全国。 */
public final class ProjectScopeSql {
    private ProjectScopeSql() { }

    public static String named(ProjectDataScope scope, String provinceColumn, String cityColumn,
            MapSqlParameterSource parameters) {
        return switch (scope.level()) {
            case NATIONAL -> "";
            case PROVINCE -> {
                parameters.addValue("scopeProvinces", scope.provinceIds().stream().sorted().map(ProjectScopeSql::bytes).toList());
                yield " AND " + provinceColumn + " IN (:scopeProvinces) ";
            }
            case CITY -> {
                parameters.addValue("scopeCity", bytes(scope.organizationId()));
                yield " AND " + cityColumn + " = :scopeCity ";
            }
        };
    }

    public static Positional positional(ProjectDataScope scope, String provinceColumn, String cityColumn) {
        return switch (scope.level()) {
            case NATIONAL -> new Positional("", List.of());
            case PROVINCE -> new Positional(" AND " + provinceColumn + " IN ("
                    + String.join(",", Collections.nCopies(scope.provinceIds().size(), "?")) + ") ",
                    scope.provinceIds().stream().sorted().map(id -> (Object) bytes(id)).toList());
            case CITY -> new Positional(" AND " + cityColumn + " = ? ", Collections.singletonList(bytes(scope.organizationId())));
        };
    }

    public static List<byte[]> binaryIds(List<UUID> ids) { return ids.stream().map(ProjectScopeSql::bytes).toList(); }

    private static byte[] bytes(UUID id) {
        return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
    public record Positional(String sql, List<Object> arguments) {
        public Object[] after(Object initial) {
            var result = new ArrayList<Object>(); result.add(initial); result.addAll(arguments); return result.toArray();
        }
    }
}
