package com.pluto.ndi.backup.mapper;

import com.pluto.ndi.backup.domain.BackupTask;
import com.pluto.ndi.backup.service.BackupArchiveService;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 任务状态更新使用工作令牌约束，重启后旧工作进程不得回写成功。 */
@Repository
public class BackupTaskMapper {
    private static final String COLUMNS = """
            BIN_TO_UUID(id) id,BIN_TO_UUID(requested_by) requested_by,status,requested_at,started_at,finished_at,
            BIN_TO_UUID(worker_token) worker_token,archive_bytes,sha256_hex,snapshot_at,table_count,object_count,error_code
            """;
    private final JdbcTemplate jdbc;
    public BackupTaskMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<BackupTask> find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM system_backup_task WHERE id=UUID_TO_BIN(?)", this::map, id.toString()).stream().findFirst();
    }
    public Optional<BackupTask> findCurrent(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM system_backup_task WHERE id=UUID_TO_BIN(?) FOR UPDATE", this::map, id.toString()).stream().findFirst();
    }
    public Page<BackupTask> list(Pageable page) {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM system_backup_task", Long.class);
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM system_backup_task ORDER BY requested_at DESC,id DESC LIMIT ? OFFSET ?", this::map, page.getPageSize(), page.getOffset());
        return new PageImpl<>(rows, page, count);
    }
    public void insert(UUID id, UUID actor, Instant at) {
        jdbc.update("INSERT INTO system_backup_task (id,requested_by,status,active_slot,requested_at) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),'PENDING',1,?)", id.toString(), actor.toString(), utc(at));
    }
    public Optional<BackupTask> pendingLocked() {
        return jdbc.query("SELECT " + COLUMNS + " FROM system_backup_task WHERE status='PENDING' ORDER BY requested_at,id LIMIT 1 FOR UPDATE SKIP LOCKED", this::map).stream().findFirst();
    }
    public void start(UUID id, UUID token, Instant at) {
        jdbc.update("UPDATE system_backup_task SET status='RUNNING',worker_token=UUID_TO_BIN(?),started_at=? WHERE id=UUID_TO_BIN(?) AND status='PENDING'", token.toString(), utc(at), id.toString());
    }
    public int finish(UUID id, UUID token, BackupArchiveService.Archive archive, Instant at) {
        return jdbc.update("""
                UPDATE system_backup_task SET status='SUCCEEDED',active_slot=NULL,finished_at=?,archive_bytes=?,sha256_hex=?,
                snapshot_at=?,table_count=?,object_count=? WHERE id=UUID_TO_BIN(?) AND status='RUNNING' AND worker_token=UUID_TO_BIN(?)
                """, utc(at), archive.bytes(), archive.sha256(), utc(archive.manifest().snapshotAt()), archive.manifest().tables().size(), archive.manifest().objects().size(), id.toString(), token.toString());
    }
    public int fail(UUID id, UUID token, String code, Instant at) {
        return jdbc.update("UPDATE system_backup_task SET status='FAILED',active_slot=NULL,finished_at=?,error_code=? WHERE id=UUID_TO_BIN(?) AND status='RUNNING' AND worker_token=UUID_TO_BIN(?)", utc(at), code, id.toString(), token.toString());
    }
    public List<BackupTask> runningLocked() {
        return jdbc.query("SELECT " + COLUMNS + " FROM system_backup_task WHERE status='RUNNING' FOR UPDATE", this::map);
    }
    private BackupTask map(ResultSet rows, int index) throws SQLException {
        var token = rows.getString("worker_token");
        return new BackupTask(UUID.fromString(rows.getString("id")), UUID.fromString(rows.getString("requested_by")), rows.getString("status"),
                instant(rows,"requested_at"), instant(rows,"started_at"), instant(rows,"finished_at"), token == null ? null : UUID.fromString(token),
                (Long) rows.getObject("archive_bytes"), rows.getString("sha256_hex"), instant(rows,"snapshot_at"), (Integer) rows.getObject("table_count"), (Integer) rows.getObject("object_count"), rows.getString("error_code"));
    }
    private static LocalDateTime utc(Instant at) { return LocalDateTime.ofInstant(at, ZoneOffset.UTC); }
    private static Instant instant(ResultSet rows, String column) throws SQLException { var value = rows.getObject(column, LocalDateTime.class); return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
