package com.pluto.ndi.projects.mapper;

import com.pluto.ndi.projects.domain.ProjectResponsiblePerson;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/** 人员批量投影和写入，避免列表逐项目访问目录。 */
@Repository
public class ProjectResponsiblePersonMapper {
    private final NamedParameterJdbcTemplate jdbc;

    public ProjectResponsiblePersonMapper(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<UUID, List<ProjectResponsiblePerson>> find(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new LinkedHashMap<UUID, List<ProjectResponsiblePerson>>();
        var parameters = new HashMap<String, Object>();
        var expressions = new ArrayList<String>();
        for (var id : ids) {
            String key = "p" + expressions.size();
            parameters.put(key, id.toString());
            expressions.add("UUID_TO_BIN(:" + key + ")");
        }
        jdbc.query(
                """
SELECT BIN_TO_UUID(p.id) id,BIN_TO_UUID(p.project_id) project_id,p.person_name,p.responsibility,
  BIN_TO_UUID(p.user_id) user_id,u.display_name,COALESCE(u.enabled,FALSE) enabled
FROM project_responsible_person p LEFT JOIN identity_user_account u ON u.id=p.user_id
WHERE p.project_id IN (%s) ORDER BY p.project_id,p.sort_order,p.id
"""
                        .formatted(String.join(",", expressions)),
                parameters,
                row -> {
                    var user = row.getString("user_id");
                    result.computeIfAbsent(
                                    UUID.fromString(row.getString("project_id")),
                                    key -> new ArrayList<>())
                            .add(
                                    new ProjectResponsiblePerson(
                                            UUID.fromString(row.getString("id")),
                                            row.getString("person_name"),
                                            row.getString("responsibility"),
                                            user == null ? null : UUID.fromString(user),
                                            row.getString("display_name"),
                                            row.getBoolean("enabled")));
                });
        return result;
    }

    public void replace(UUID projectId, List<ProjectResponsiblePerson> people) {
        jdbc.update(
                "DELETE FROM project_responsible_person WHERE project_id=UUID_TO_BIN(:id)",
                Map.of("id", projectId.toString()));
        for (int index = 0; index < people.size(); index++) {
            var person = people.get(index);
            var values = new HashMap<String, Object>();
            values.put("id", person.id().toString());
            values.put("project", projectId.toString());
            values.put("name", person.name());
            values.put("role", person.responsibility());
            values.put("user", person.userId() == null ? null : person.userId().toString());
            values.put("sort", index);
            jdbc.update(
                    """
INSERT INTO project_responsible_person (id,project_id,person_name,responsibility,user_id,sort_order)
VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:project),:name,:role,UUID_TO_BIN(:user),:sort)
""",
                    values);
        }
    }
}
