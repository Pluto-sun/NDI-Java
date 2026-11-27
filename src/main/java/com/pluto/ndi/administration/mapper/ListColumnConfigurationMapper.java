package com.pluto.ndi.administration.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.administration.domain.ListColumnCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ListColumnConfigurationMapper {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ListColumnConfigurationMapper(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<String> read(ListColumnCatalog list) {
        return select(list, false);
    }

    public List<String> lock(ListColumnCatalog list) {
        // 首次配置也需要行锁，确保并发写入的审计前值准确。
        jdbc.update("""
                INSERT INTO list_column_configuration(list_id, fixed_fields) VALUES (?, JSON_ARRAY())
                ON DUPLICATE KEY UPDATE list_id = list_id
                """, list.name());
        return select(list, true);
    }

    private List<String> select(ListColumnCatalog list, boolean lock) {
        var rows = jdbc.query("SELECT fixed_fields FROM list_column_configuration WHERE list_id = ?"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> decode(rs.getString(1)), list.name());
        return rows.isEmpty() ? List.of() : rows.getFirst();
    }

    public void save(ListColumnCatalog list, List<String> fields) {
        try {
            jdbc.update("UPDATE list_column_configuration SET fixed_fields = CAST(? AS JSON) WHERE list_id = ?",
                    json.writeValueAsString(fields), list.name());
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("固定列配置无法序列化", failure);
        }
    }

    private List<String> decode(String value) {
        try {
            return json.readValue(value, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("固定列配置无法读取", failure);
        }
    }
}
