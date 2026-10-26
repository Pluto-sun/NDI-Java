package com.pluto.ndi.projects.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.projects.domain.ProjectImportTask;
import com.pluto.ndi.projects.domain.ProjectImportTask.Configuration;
import com.pluto.ndi.projects.util.ProjectExcelReader;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class ProjectImportTaskMapper {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProjectImportTaskMapper(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 同账号首次创建也串行，防止两个标签页创建两项活动任务。 */
    public void lock(UUID actor) {
        jdbc.update(
                "INSERT INTO project_import_actor_lock(actor_id) VALUES(UUID_TO_BIN(?)) ON"
                    + " DUPLICATE KEY UPDATE actor_id=actor_id",
                actor.toString());
        jdbc.queryForObject(
                "SELECT BIN_TO_UUID(actor_id) FROM project_import_actor_lock WHERE"
                        + " actor_id=UUID_TO_BIN(?) FOR UPDATE",
                String.class,
                actor.toString());
    }

    public Optional<ProjectImportTask> find(UUID actor) {
        return jdbc
                .query(
                        "SELECT * FROM project_import_task WHERE actor_id=UUID_TO_BIN(?)",
                        this::row,
                        actor.toString())
                .stream()
                .findFirst();
    }

    public Optional<ProjectImportTask> findLocked(UUID actor) {
        return jdbc
                .query(
                        "SELECT * FROM project_import_task WHERE actor_id=UUID_TO_BIN(?) FOR"
                                + " UPDATE",
                        this::row,
                        actor.toString())
                .stream()
                .findFirst();
    }

    public Optional<ProjectImportTask> findByBatch(UUID batch) {
        return jdbc
                .query(
                        "SELECT * FROM project_import_task WHERE batch_id=UUID_TO_BIN(?)",
                        this::row,
                        batch.toString())
                .stream()
                .findFirst();
    }

    public void save(ProjectImportTask t) {
        jdbc.update(
                """
INSERT INTO project_import_task(id,actor_id,version_no,step_no,file_name,bucket_name,object_key,byte_size,sha256_hex,
    inspection_json,sheet_name,header_row_number,field_mapping,selected_rows,preview_configuration,batch_id,updated_at)
VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?,?,?,?,UUID_TO_BIN(?),?)
ON DUPLICATE KEY UPDATE id=VALUES(id),version_no=VALUES(version_no),step_no=VALUES(step_no),file_name=VALUES(file_name),
    bucket_name=VALUES(bucket_name),object_key=VALUES(object_key),byte_size=VALUES(byte_size),sha256_hex=VALUES(sha256_hex),
    inspection_json=VALUES(inspection_json),sheet_name=VALUES(sheet_name),header_row_number=VALUES(header_row_number),
    field_mapping=VALUES(field_mapping),selected_rows=VALUES(selected_rows),preview_configuration=VALUES(preview_configuration),
    batch_id=VALUES(batch_id),updated_at=VALUES(updated_at)
""",
                t.id().toString(),
                t.actorId().toString(),
                t.version(),
                t.step(),
                t.fileName(),
                t.bucket(),
                t.objectKey(),
                t.byteSize(),
                t.sha256(),
                write(t.inspection()),
                t.configuration().sheetName(),
                t.configuration().headerRowNo(),
                write(t.configuration().fieldMapping()),
                t.selectedRows() == null ? null : write(t.selectedRows()),
                t.previewConfiguration() == null ? null : write(t.previewConfiguration()),
                t.batchId() == null ? null : t.batchId().toString(),
                Timestamp.from(t.updatedAt()));
    }

    public void delete(UUID actor) {
        jdbc.update(
                "DELETE FROM project_import_task WHERE actor_id=UUID_TO_BIN(?)", actor.toString());
    }

    private ProjectImportTask row(ResultSet r, int index) throws SQLException {
        String batch = r.getString("batch_id");
        return new ProjectImportTask(
                uuid(r, "id"),
                uuid(r, "actor_id"),
                r.getLong("version_no"),
                r.getInt("step_no"),
                r.getString("file_name"),
                r.getString("bucket_name"),
                r.getString("object_key"),
                r.getLong("byte_size"),
                r.getString("sha256_hex"),
                read(r.getString("inspection_json"), ProjectExcelReader.WorkbookInspection.class),
                new Configuration(
                        r.getString("sheet_name"),
                        (Integer) r.getObject("header_row_number"),
                        read(
                                r.getString("field_mapping"),
                                new TypeReference<Map<String, String>>() {})),
                read(r.getString("selected_rows"), new TypeReference<List<Integer>>() {}),
                read(r.getString("preview_configuration"), Configuration.class),
                batch == null ? null : uuid(r, "batch_id"),
                r.getTimestamp("updated_at").toInstant());
    }

    private UUID uuid(ResultSet r, String column) throws SQLException {
        var b = r.getBytes(column);
        var buffer = java.nio.ByteBuffer.wrap(b);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("导入任务序列化失败", e);
        }
    }

    private <T> T read(String value, Class<T> type) {
        if (value == null) return null;
        try {
            return json.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException("导入任务解析失败", e);
        }
    }

    private <T> T read(String value, TypeReference<T> type) {
        if (value == null) return null;
        try {
            return json.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException("导入任务解析失败", e);
        }
    }
}
