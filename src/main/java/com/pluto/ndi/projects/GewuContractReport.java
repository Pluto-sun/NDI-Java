package com.pluto.ndi.projects;

import com.pluto.ndi.identity.service.IdentityDirectory;
import com.pluto.ndi.identity.service.ProjectActor;
import com.pluto.ndi.identity.service.ProjectDataScope.Level;
import com.pluto.ndi.projects.domain.ProjectErrorCode;
import com.pluto.ndi.projects.mapper.ProjectCurrentMetricSql;
import com.pluto.ndi.projects.mapper.ProjectScopeSql;
import com.pluto.ndi.shared.web.BusinessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 为计收驾驶舱提供当前有权查看的格物待计收项目。 */
@Service
public class GewuContractReport {
    private final IdentityDirectory identities;
    private final NamedParameterJdbcTemplate jdbc;

    public GewuContractReport(IdentityDirectory identities, NamedParameterJdbcTemplate jdbc) {
        this.identities = identities;
        this.jdbc = jdbc;
    }

    public Result load(UUID user, int year) {
        var scope = identities.findProjectActor(user).filter(ProjectActor::enabled)
                .flatMap(identities::findReadableProjectScope)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.ACCESS_FORBIDDEN));
        var params = new MapSqlParameterSource("start", LocalDate.of(year, 1, 1))
                .addValue("end", LocalDate.of(year + 1, 1, 1));
        var month = ProjectCurrentMetricSql.ATTRIBUTION_MONTH;
        var restriction = ProjectScopeSql.named(scope, "project.province_id", "project.city_id", params);
        var rows = jdbc.query("""
                SELECT project.contract_no,BIN_TO_UUID(v.id) province_id,v.province_name,c.expected_amount
                FROM project_project project
                JOIN project_category_amount c ON c.project_id=project.id AND c.category_code='GEWU'
                JOIN organization_province v ON v.id=project.province_id
                WHERE project.deleted_at IS NULL AND project.current_stage_code='COLLECTION'
                """ + " AND " + month + ">=:start AND " + month + "<:end" + restriction, params,
                (rs, index) -> new Contract(rs.getString("contract_no"), rs.getString("province_id"),
                        rs.getString("province_name"), rs.getBigDecimal("expected_amount")));
        return new Result(scope.level(), rows);
    }

    public record Contract(String number, String provinceId, String provinceName, BigDecimal amount) {}
    public record Result(Level scope, List<Contract> contracts) {}
}
