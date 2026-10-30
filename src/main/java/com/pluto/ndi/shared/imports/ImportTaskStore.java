package com.pluto.ndi.shared.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.shared.imports.ImportModels.Task;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.*;

public class ImportTaskStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String module;

    public ImportTaskStore(NamedParameterJdbcTemplate jdbc, ObjectMapper json, String module) {
        if (!Set.of("production", "revenue").contains(module))
            throw new IllegalArgumentException("导入模块无效");
        this.module = module;
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Stored(Task task, String bucket, String key, long size, String sha) {}

    public void lock(UUID actor) {
        var args = Map.of("actor", actor.toString());
        jdbc.update(
                table(
                        "INSERT INTO revenue_import_lock(actor_id) VALUES(UUID_TO_BIN(:actor)) ON"
                                + " DUPLICATE KEY UPDATE actor_id=actor_id"),
                args);
        jdbc.queryForList(
                table(
                        "SELECT actor_id FROM revenue_import_lock WHERE"
                                + " actor_id=UUID_TO_BIN(:actor) FOR UPDATE"),
                args);
    }

    public Optional<Stored> find(UUID actor) {
        return jdbc
                .query(
                        table(
                                "SELECT * FROM revenue_import_task WHERE"
                                        + " actor_id=UUID_TO_BIN(:actor) FOR UPDATE"),
                        Map.of("actor", actor.toString()),
                        (rs, row) ->
                                new Stored(
                                        decode(rs.getString("task_json")),
                                        rs.getString("bucket_name"),
                                        rs.getString("object_key"),
                                        rs.getLong("byte_size"),
                                        rs.getString("sha256_hex")))
                .stream()
                .findFirst();
    }

    public void save(UUID actor, Stored stored) {
        jdbc.update(
                table(
                        """
INSERT INTO revenue_import_task(actor_id,id,bucket_name,object_key,task_json,updated_at,byte_size,sha256_hex)
 VALUES(UUID_TO_BIN(:actor),UUID_TO_BIN(:id),:bucket,:key,CAST(:task AS JSON),:at,:size,:sha)
ON DUPLICATE KEY UPDATE id=VALUES(id),bucket_name=VALUES(bucket_name),object_key=VALUES(object_key),task_json=VALUES(task_json),updated_at=VALUES(updated_at),byte_size=VALUES(byte_size),sha256_hex=VALUES(sha256_hex)
"""),
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                                "actor", actor.toString())
                        .addValue("id", stored.task().id().toString())
                        .addValue("bucket", stored.bucket())
                        .addValue("key", stored.key())
                        .addValue("task", encode(stored.task()))
                        .addValue("at", java.sql.Timestamp.from(Instant.now()))
                        .addValue("size", stored.size())
                        .addValue("sha", stored.sha()));
    }

    public void delete(UUID actor) {
        jdbc.update(
                table("DELETE FROM revenue_import_task WHERE actor_id=UUID_TO_BIN(:actor)"),
                Map.of("actor", actor.toString()));
    }

    private String table(String sql) {
        return sql.replace("revenue_import_", module + "_import_");
    }

    private Task decode(String value) {
        try {
            return json.readValue(value, Task.class);
        } catch (Exception e) {
            throw new IllegalStateException("导入任务读取失败", e);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
