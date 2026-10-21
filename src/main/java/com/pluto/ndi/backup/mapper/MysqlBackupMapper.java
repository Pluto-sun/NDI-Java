package com.pluto.ndi.backup.mapper;

import com.pluto.ndi.backup.config.BackupProperties;
import com.pluto.ndi.backup.domain.BackupFailure;
import com.pluto.ndi.backup.domain.BackupManifest;
import com.pluto.ndi.documents.service.BackupObjectDirectory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.pluto.ndi.backup.domain.BackupFailure.Reason.*;

/** 仅使用调用方持有的同一快照连接，禁止在查询中另开事务。 */
@Repository
public class MysqlBackupMapper {
    private final BackupProperties properties;
    private final BackupObjectDirectory objects;
    public MysqlBackupMapper(BackupProperties properties, BackupObjectDirectory objects) {
        this.properties = properties; this.objects = objects;
    }
    public record ObjectRef(String bucket, String key, String mediaType, long size, String sha256) { }

    public SortedMap<String, String> schema(Connection connection) throws SQLException {
        var tables = new TreeMap<String, String>();
        try (var query = connection.createStatement(); var rows = query.executeQuery("""
                SELECT TABLE_NAME, TABLE_TYPE, ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
                """)) {
            while (rows.next()) {
                if (!"BASE TABLE".equals(rows.getString(2)) || !"InnoDB".equalsIgnoreCase(rows.getString(3))) {
                    throw new BackupFailure(UNSUPPORTED_SCHEMA);
                }
                tables.put(rows.getString(1), "");
            }
        }
        // 当前业务只使用普通表；遇到不能完整还原的对象时明确失败，禁止静默遗漏。
        for (var kind : List.of("TRIGGERS", "ROUTINES", "EVENTS")) {
            var column = kind.equals("TRIGGERS") ? "TRIGGER_SCHEMA" : kind.equals("ROUTINES") ? "ROUTINE_SCHEMA" : "EVENT_SCHEMA";
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM information_schema." + kind + " WHERE " + column + " = DATABASE()")) {
                rows.next(); if (rows.getLong(1) != 0) throw new BackupFailure(UNSUPPORTED_SCHEMA);
            }
        }
        for (var table : tables.keySet()) {
            try (var query = connection.createStatement(); var rows = query.executeQuery("SHOW CREATE TABLE " + quote(table))) {
                rows.next(); tables.put(table, rows.getString(2));
            }
        }
        return tables;
    }

    public List<ObjectRef> references(Connection connection) throws SQLException {
        var result = new ArrayList<ObjectRef>();
        try (var query = connection.createStatement(); var rows = query.executeQuery("""
                SELECT bucket_name, object_key, media_type, byte_size, sha256_hex FROM document_object
                WHERE status IN ('AVAILABLE', 'DELETED') ORDER BY id
                """)) {
            while (rows.next()) {
                result.add(new ObjectRef(rows.getString(1), rows.getString(2), rows.getString(3), rows.getLong(4), rows.getString(5)));
                if (result.size() > properties.maxObjects()) throw new BackupFailure(LIMIT_EXCEEDED);
            }
        }
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT BIN_TO_UUID(id) FROM project_export_task WHERE status = 'COMPLETED' ORDER BY id")) {
            while (rows.next()) {
                var location = objects.projectExportLocation(UUID.fromString(rows.getString(1)));
                result.add(new ObjectRef(location.bucket(), location.objectKey(), "text/csv; charset=utf-8", -1, null));
                if (result.size() > properties.maxObjects()) throw new BackupFailure(LIMIT_EXCEEDED);
            }
        }
        return result;
    }

    public long dump(Connection connection, String table, Writer sql, Runnable checkBudget) throws SQLException, IOException {
        long count = 0;
        var generated = new HashSet<String>();
        try (var query = connection.prepareStatement("""
                SELECT COLUMN_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND GENERATION_EXPRESSION <> ''
                """)) {
            query.setString(1, table);
            try (var rows = query.executeQuery()) { while (rows.next()) generated.add(rows.getString(1)); }
        }
        try (var query = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            query.setFetchSize(Integer.MIN_VALUE);
            query.setQueryTimeout(properties.timeoutSeconds());
            try (var rows = query.executeQuery("SELECT * FROM " + quote(table))) {
                var meta = rows.getMetaData();
                var columns = new ArrayList<Integer>();
                var names = new ArrayList<String>();
                for (int index = 1; index <= meta.getColumnCount(); index++) {
                    // 生成列表达式由建表语句恢复，不能作为普通值插入。
                    if (!generated.contains(meta.getColumnName(index))) { columns.add(index); names.add(quote(meta.getColumnName(index))); }
                }
                if (columns.isEmpty()) throw new BackupFailure(UNSUPPORTED_SCHEMA);
                String prefix = "INSERT INTO " + quote(table) + " (" + String.join(",", names) + ") VALUES (";
                while (rows.next()) {
                    checkBudget.run();
                    sql.write(prefix);
                    for (int offset = 0; offset < columns.size(); offset++) {
                        if (offset > 0) sql.write(",");
                        int column = columns.get(offset);
                        int type = meta.getColumnType(column);
                        // 驱动可能将 TINYINT(1) 映射为 BIT；使用数值还原，避免十六进制字符“0”变成数值 48。
                        if (type == Types.BIT || type == Types.BOOLEAN) {
                            var number = rows.getBigDecimal(column);
                            sql.write(number == null ? "NULL" : number.toPlainString());
                            continue;
                        }
                        boolean binary = type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == Types.BLOB;
                        var value = binary ? rows.getBytes(column) : Optional.ofNullable(rows.getString(column)).map(text -> text.getBytes(StandardCharsets.UTF_8)).orElse(null);
                        if (value == null) sql.write("NULL");
                        else {
                            if (value.length > 16 * 1024 * 1024) throw new BackupFailure(LIMIT_EXCEEDED);
                            sql.write(binary ? "X'" : "CONVERT(X'");
                            for (int position = 0; position < value.length; position += 4096) {
                                sql.write(HexFormat.of().formatHex(value, position, Math.min(position + 4096, value.length)));
                            }
                            sql.write(binary ? "'" : "' USING utf8mb4)");
                        }
                    }
                    sql.write(");\n"); count++;
                }
            }
        }
        return count;
    }

    private static String quote(String name) { return "`" + name.replace("`", "``") + "`"; }
}
