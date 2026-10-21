package com.pluto.ndi.backup.mapper;

import com.pluto.ndi.backup.domain.*;
import java.sql.*;
import java.util.*;
import static com.pluto.ndi.backup.domain.RestoreFailure.Reason.*;

/** 只向已验证的空目标库写入；恢复成功前不移交业务服务使用。 */
public final class RestoreTargetMapper {
    private RestoreTargetMapper() { }
    public static void requireEmpty(Connection connection) throws SQLException {
        try (var query=connection.createStatement();var rows=query.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
            rows.next(); if(rows.getLong(1)!=0) throw new RestoreFailure(TARGET_NOT_EMPTY);
        }
        for(var kind:List.of("ROUTINES","EVENTS")) {
            String column=kind.equals("ROUTINES")?"ROUTINE_SCHEMA":"EVENT_SCHEMA";
            try(var query=connection.createStatement();var rows=query.executeQuery("SELECT COUNT(*) FROM information_schema."+kind+" WHERE "+column+"=DATABASE()")) {
                rows.next(); if(rows.getLong(1)!=0) throw new RestoreFailure(TARGET_NOT_EMPTY);
            }
        }
    }
    public static void verifyRows(Connection connection,BackupManifest manifest) throws SQLException {
        for(var table:manifest.tables()) try(var query=connection.createStatement();var rows=query.executeQuery("SELECT COUNT(*) FROM "+quote(table.name()))) {
            rows.next(); if(rows.getLong(1)!=table.rows()) throw new RestoreFailure(RESTORE_FAILED);
        }
        try(var query=connection.createStatement();var rows=query.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
            rows.next(); if(rows.getLong(1)!=manifest.tables().size()) throw new RestoreFailure(RESTORE_FAILED);
        }
    }
    private record Key(String table,String name,String parent) { }
    private record Columns(String child,String parent) { }
    public static void verifyForeignKeys(Connection connection) throws SQLException {
        var keys=new LinkedHashMap<Key,List<Columns>>();
        try(var query=connection.createStatement();var rows=query.executeQuery("""
                SELECT TABLE_NAME,CONSTRAINT_NAME,REFERENCED_TABLE_NAME,COLUMN_NAME,REFERENCED_COLUMN_NAME,
                REFERENCED_TABLE_SCHEMA FROM information_schema.KEY_COLUMN_USAGE
                WHERE CONSTRAINT_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION
                """)) {
            while(rows.next()) {
                if(!connection.getCatalog().equals(rows.getString(6))) throw new RestoreFailure(RESTORE_FAILED);
                keys.computeIfAbsent(new Key(rows.getString(1),rows.getString(2),rows.getString(3)),ignored->new ArrayList<>()).add(new Columns(rows.getString(4),rows.getString(5)));
            }
        }
        for(var entry:keys.entrySet()) {
            var joins=entry.getValue().stream().map(column->"c."+quote(column.child())+"=p."+quote(column.parent())).toList();
            var nonnull=entry.getValue().stream().map(column->"c."+quote(column.child())+" IS NOT NULL").toList();
            String sql="SELECT COUNT(*) FROM "+quote(entry.getKey().table())+" c LEFT JOIN "+quote(entry.getKey().parent())+" p ON "+String.join(" AND ",joins)
                    +" WHERE "+String.join(" AND ",nonnull)+" AND p."+quote(entry.getValue().getFirst().parent())+" IS NULL";
            try(var query=connection.createStatement();var rows=query.executeQuery(sql)) {rows.next();if(rows.getLong(1)!=0) throw new RestoreFailure(RESTORE_FAILED);}
        }
    }
    public record Cleanup(int invalidatedSessions,int interruptedBackups) { }
    public static Cleanup sanitizeOperationalState(Connection connection,Set<String> tables,BackupManifest manifest,String operator,String checksum) throws SQLException {
        int sessions=0;int backups=0;
        // 历史会话不得在恢复后复活；快照中的运行任务也不得自动重复创建归档。
        if(tables.contains("identity_user_session")) try(var query=connection.createStatement()) {sessions=query.executeUpdate("DELETE FROM identity_user_session");}
        if(tables.contains("system_backup_task")) try(var query=connection.createStatement()) {
            backups=query.executeUpdate("UPDATE system_backup_task SET status='FAILED',active_slot=NULL,finished_at=UTC_TIMESTAMP(6),error_code='RESTORED' WHERE status IN ('PENDING','RUNNING')");
        }
        try(var query=connection.prepareStatement("""
                INSERT INTO system_audit_event(id,actor_id,event_type,resource_type,resource_id,detail,occurred_at)
                VALUES(UUID_TO_BIN(?),NULL,'BACKUP_RESTORED','BACKUP',UUID_TO_BIN(?),JSON_OBJECT('operator',?,'sha256',?,'invalidatedSessions',?,'interruptedBackups',?),UTC_TIMESTAMP(6))
                """)) {
            query.setString(1,UUID.randomUUID().toString());query.setString(2,manifest.backupId().toString());query.setString(3,operator);query.setString(4,checksum);query.setInt(5,sessions);query.setInt(6,backups);query.executeUpdate();
        }
        return new Cleanup(sessions,backups);
    }
    private static String quote(String name) { return "`"+name.replace("`","``")+"`"; }
}
