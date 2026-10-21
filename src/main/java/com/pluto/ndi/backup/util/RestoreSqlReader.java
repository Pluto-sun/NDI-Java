package com.pluto.ndi.backup.util;

import java.io.*;
import java.util.Set;
import java.util.regex.Pattern;

/** 流式处理本系统格式化 SQL；限制单句内存，拒绝跨库操作及归档范围外的表。 */
public final class RestoreSqlReader {
    private static final int MAX_STATEMENT_CHARS = 64 * 1024 * 1024;
    private static final Pattern TABLE = Pattern.compile("^(?:CREATE TABLE|INSERT INTO) `([a-zA-Z0-9_]+)`[\\s(].*",Pattern.DOTALL);
    private RestoreSqlReader() { }
    @FunctionalInterface public interface StatementConsumer { void accept(String sql) throws Exception; }
    public static void read(Reader source, Set<String> tables, StatementConsumer consumer) throws Exception {
        var reader = new PushbackReader(new BufferedReader(source),2);
        var statement = new StringBuilder();
        int quote = 0; boolean escaped = false; boolean lineComment = false; boolean blockComment = false; int previous = 0;
        int value;
        while ((value=reader.read()) != -1) {
            char character=(char)value;
            if (lineComment) { if (character=='\n') { lineComment=false; statement.append('\n'); } continue; }
            if (blockComment) { statement.append(character); if(statement.length()>MAX_STATEMENT_CHARS) throw new IOException("SQL 单句超过恢复限制"); if (previous=='*' && character=='/') blockComment=false; previous=character; continue; }
            if (quote != 0) {
                statement.append(character);
                if (escaped) escaped=false;
                else if (character=='\\' && quote!='`') escaped=true;
                else if (character==quote) quote=0;
            } else if (character=='\'' || character=='"' || character=='`') { quote=character; statement.append(character); }
            else if (character=='-' || character=='/') {
                int next=reader.read();
                if (character=='-' && next=='-') { lineComment=true; continue; }
                if (character=='/' && next=='*') { blockComment=true; previous=0; statement.append("/*"); continue; }
                if (next!=-1) reader.unread(next); statement.append(character);
            } else if (character==';') {
                consume(statement.toString().trim(),tables,consumer); statement.setLength(0);
            } else statement.append(character);
            if (statement.length()>MAX_STATEMENT_CHARS) throw new IOException("SQL 单句超过恢复限制");
        }
        if (quote!=0 || blockComment || !statement.toString().isBlank()) throw new IOException("SQL 归档未完整结束");
    }
    private static void consume(String sql,Set<String> tables,StatementConsumer consumer) throws Exception {
        if (sql.isEmpty()) return;
        if (Set.of("SET NAMES utf8mb4","SET time_zone='+00:00'","SET FOREIGN_KEY_CHECKS=0","SET FOREIGN_KEY_CHECKS=1").contains(sql)) { consumer.accept(sql); return; }
        var match=TABLE.matcher(sql);
        if (!match.matches() || !tables.contains(match.group(1))) throw new IOException("SQL 包含不支持的恢复操作");
        consumer.accept(sql);
    }
}
