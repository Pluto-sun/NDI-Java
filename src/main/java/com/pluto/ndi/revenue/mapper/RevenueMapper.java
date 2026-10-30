package com.pluto.ndi.revenue.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluto.ndi.revenue.domain.Revenue.*;
import com.pluto.ndi.revenue.service.RevenueAccessService.Access;
import com.pluto.ndi.revenue.util.RevenueFields.Source;

import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.*;
import java.util.*;

/** 计收持久化边界，条目先加锁，审批及交接随后按同一顺序处理。 */
@Repository
public class RevenueMapper {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public RevenueMapper(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private static final String SELECT =
            """
SELECT e.*, BIN_TO_UUID(e.id) eid, BIN_TO_UUID(e.province_id) province,
 BIN_TO_UUID(e.recognized_by) recognizer,
 (SELECT BIN_TO_UUID(r.id) FROM revenue_recognition_request r WHERE r.entry_id=e.id AND r.status='PENDING') pending
 FROM revenue_entry e
""";

    public record Filter(
            String month, String province, String search, String state, int page, int size) {}

    public record Summary(
            long total,
            long unrecognized,
            long recognized,
            long pending,
            @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                            using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
                    java.math.BigDecimal recognizedAmount) {}

    public record Listing(
            List<Entry> items,
            long totalElements,
            int totalPages,
            int page,
            int size,
            Summary summary) {}

    private record Where(String sql, MapSqlParameterSource args) {}

    private Where where(Access a, Filter f) {
        var sql = new StringBuilder(" WHERE 1=1");
        var args = new MapSqlParameterSource();
        if (!a.national()) {
            sql.append(" AND e.province_id=UUID_TO_BIN(:scope) AND e.specific_organization=FALSE");
            args.addValue("scope", str(a.user().provinceId()));
        }
        if (f.month() != null && !f.month().isBlank()) {
            sql.append(" AND e.account_month=:month");
            args.addValue("month", f.month() + "-01");
        }
        if (f.province() != null && !f.province().isBlank()) {
            if (f.province().equals("ORGANIZATION"))
                sql.append(" AND e.specific_organization=TRUE");
            else {
                sql.append(" AND e.province_id=UUID_TO_BIN(:province)");
                args.addValue("province", f.province());
            }
        }
        if (f.search() != null && !f.search().isBlank()) {
            sql.append(
                    " AND (e.contract_no LIKE :q OR e.clause_no LIKE :q OR"
                            + " JSON_UNQUOTE(JSON_EXTRACT(e.source_fields,'$.\"项目名称\"')) LIKE :q OR"
                            + " JSON_UNQUOTE(JSON_EXTRACT(e.source_fields,'$.\"客户名称\"')) LIKE :q OR"
                            + " JSON_UNQUOTE(JSON_EXTRACT(e.source_fields,'$.\"业务号码/项目编号\"')) LIKE"
                            + " :q)");
            args.addValue(
                    "q",
                    "%"
                            + f.search().replace("!", "!!").replace("%", "!%").replace("_", "!_")
                            + "%");
            // LIKE 的转义字符单独声明，避免用户输入成为通配符。
            var text = sql.toString().replace("LIKE :q", "LIKE :q ESCAPE '!'");
            sql = new StringBuilder(text);
        }
        String pending =
                "EXISTS(SELECT 1 FROM revenue_recognition_request r WHERE r.entry_id=e.id AND"
                        + " r.status='PENDING')";
        switch (Objects.toString(f.state(), "")) {
            case "UNRECOGNIZED" -> sql.append(" AND e.recognition IS NULL AND NOT " + pending);
            case "PENDING" -> sql.append(" AND e.recognition IS NULL AND " + pending);
            case "RECOGNIZED" -> sql.append(" AND e.recognition IS NOT NULL AND NOT " + pending);
            case "CHANGE_PENDING" -> sql.append(" AND e.recognition IS NOT NULL AND " + pending);
        }
        return new Where(sql.toString(), args);
    }

    public Listing list(Access a, Filter f) {
        var w = where(a, f);
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM revenue_entry e" + w.sql(), w.args(), Long.class);
        var summary =
                jdbc.queryForObject(
                        """
SELECT COUNT(*) total, COALESCE(SUM(e.recognition IS NULL),0) unrecognized,
 COALESCE(SUM(e.recognition IS NOT NULL),0) recognized,
 COALESCE(SUM(EXISTS(SELECT 1 FROM revenue_recognition_request r WHERE r.entry_id=e.id AND r.status='PENDING')),0) pending,
 COALESCE(SUM(e.recognized_amount),0) amount FROM revenue_entry e
"""
                                + w.sql(),
                        w.args(),
                        (r, n) ->
                                new Summary(
                                        r.getLong("total"),
                                        r.getLong("unrecognized"),
                                        r.getLong("recognized"),
                                        r.getLong("pending"),
                                        r.getBigDecimal("amount")));
        w.args().addValue("limit", f.size()).addValue("offset", f.page() * f.size());
        var items =
                jdbc.query(
                        SELECT
                                + w.sql()
                                + " ORDER BY e.account_month DESC,e.created_at DESC,e.id LIMIT"
                                + " :limit OFFSET :offset",
                        w.args(),
                        (r, n) -> entry(r, a));
        return new Listing(
                items,
                total,
                (int) ((total + f.size() - 1) / f.size()),
                f.page(),
                f.size(),
                summary);
    }

    public Optional<Entry> find(UUID id, Access access, boolean lock) {
        return jdbc
                .query(
                        SELECT + " WHERE e.id=UUID_TO_BIN(:id)" + (lock ? " FOR UPDATE" : ""),
                        Map.of("id", str(id)),
                        (r, n) -> entry(r, access))
                .stream()
                .findFirst();
    }

    private Entry entry(ResultSet r, Access a) throws SQLException {
        var province = uuid(r.getString("province"));
        boolean org = r.getBoolean("specific_organization");
        var value = decode(r.getString("recognition"), Recognition.class);
        var pending = uuid(r.getString("pending"));
        var state =
                pending == null
                        ? (value == null ? "UNRECOGNIZED" : "RECOGNIZED")
                        : (value == null ? "PENDING" : "CHANGE_PENDING");
        return new Entry(
                uuid(r.getString("eid")),
                r.getLong("version_no"),
                r.getDate("account_month").toLocalDate().toString().substring(0, 7),
                province,
                org,
                r.getString("contract_no"),
                r.getString("clause_no"),
                r.getBigDecimal("clause_amount"),
                r.getBigDecimal("source_amount"),
                decodeMap(r.getString("source_fields")),
                value,
                r.getTimestamp("created_at").toInstant(),
                instant(r, "recognized_at"),
                uuid(r.getString("recognizer")),
                state,
                pending,
                pending == null && a.recognize(province, org));
    }

    public boolean duplicate(Source c) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM revenue_entry WHERE account_month=:month AND"
                                + " contract_no=:contract AND clause_no=:clause",
                        Map.of(
                                "month",
                                c.month() + "-01",
                                "contract",
                                c.contractNo(),
                                "clause",
                                c.clauseNo()),
                        Integer.class)
                > 0;
    }

    public void insert(UUID id, UUID actor, Source c, Instant at) {
        jdbc.update(
                """
INSERT INTO revenue_entry(id,account_month,province_id,specific_organization,contract_no,clause_no,clause_amount,source_amount,source_fields,created_by,created_at,updated_at)
VALUES(UUID_TO_BIN(:id),:month,UUID_TO_BIN(:province),:org,:contract,:clause,:amount,:sourceAmount,CAST(:source AS JSON),UUID_TO_BIN(:actor),:at,:at)
""",
                new MapSqlParameterSource("id", str(id))
                        .addValue("month", c.month() + "-01")
                        .addValue("province", str(c.provinceId()))
                        .addValue("org", c.organization())
                        .addValue("contract", c.contractNo())
                        .addValue("clause", c.clauseNo())
                        .addValue("amount", c.clauseAmount())
                        .addValue("sourceAmount", c.sourceAmount())
                        .addValue("source", encode(c.fields()))
                        .addValue("actor", str(actor))
                        .addValue("at", Timestamp.from(at)));
    }

    public void bump(UUID id, Instant at) {
        jdbc.update(
                "UPDATE revenue_entry SET version_no=version_no+1,updated_at=:at WHERE"
                        + " id=UUID_TO_BIN(:id)",
                Map.of("id", str(id), "at", Timestamp.from(at)));
    }

    public void recognize(Entry e, Recognition value, UUID actor, UUID request, Instant at) {
        jdbc.update(
                """
UPDATE revenue_entry SET recognition=CAST(:value AS JSON),recognized_amount=:amount,recognized_by=UUID_TO_BIN(:actor),recognized_at=:at WHERE id=UUID_TO_BIN(:id)
""",
                new MapSqlParameterSource("id", str(e.id()))
                        .addValue("value", encode(value))
                        .addValue("amount", value.amount())
                        .addValue("actor", str(actor))
                        .addValue("at", Timestamp.from(at)));
        jdbc.update(
                """
INSERT INTO revenue_recognition_history(id,entry_id,actor_id,request_id,before_value,after_value,occurred_at)
VALUES(UUID_TO_BIN(:id),UUID_TO_BIN(:entry),UUID_TO_BIN(:actor),UUID_TO_BIN(:request),CAST(:before AS JSON),CAST(:after AS JSON),:at)
""",
                new MapSqlParameterSource("id", str(UUID.randomUUID()))
                        .addValue("entry", str(e.id()))
                        .addValue("actor", str(actor))
                        .addValue("request", str(request))
                        .addValue(
                                "before", e.recognition() == null ? null : encode(e.recognition()))
                        .addValue("after", encode(value))
                        .addValue("at", Timestamp.from(at)));
    }

    private static final String REQUEST_SELECT =
            """
SELECT r.*,BIN_TO_UUID(r.id) rid,BIN_TO_UUID(r.entry_id) entry,
 BIN_TO_UUID(r.initiator_id) initiator,BIN_TO_UUID(r.approver_id) approver,
 i.display_name initiator_name,a.display_name approver_name
 FROM revenue_recognition_request r JOIN identity_user_account i ON i.id=r.initiator_id
 JOIN identity_user_account a ON a.id=r.approver_id
""";

    public Request request(UUID id, boolean lock) {
        return jdbc
                .query(
                        REQUEST_SELECT
                                + " WHERE r.id=UUID_TO_BIN(:id)"
                                + (lock ? " FOR UPDATE" : ""),
                        Map.of("id", str(id)),
                        (r, n) -> request(r))
                .stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("计收审批不存在"));
    }

    private Request request(ResultSet r) throws SQLException {
        return new Request(
                uuid(r.getString("rid")),
                uuid(r.getString("entry")),
                uuid(r.getString("initiator")),
                uuid(r.getString("approver")),
                r.getLong("base_version"),
                r.getLong("version_no"),
                r.getString("status"),
                decode(r.getString("proposed"), Recognition.class),
                r.getString("opinion"),
                r.getTimestamp("created_at").toInstant(),
                instant(r, "decided_at"),
                r.getString("initiator_name"),
                r.getString("approver_name"));
    }

    public List<Request> requests(UUID entry) {
        return jdbc.query(
                REQUEST_SELECT
                        + " WHERE r.entry_id=UUID_TO_BIN(:id) ORDER BY r.created_at DESC,r.id",
                Map.of("id", str(entry)),
                (r, n) -> request(r));
    }

    public Page<Request> requestPage(Access access, String view, int page, int size) {
        var args = new MapSqlParameterSource("actor", str(access.user().id()));
        String condition = " WHERE 1=1";
        if (!access.national()) {
            condition += " AND e.province_id=UUID_TO_BIN(:scope) AND e.specific_organization=FALSE";
            args.addValue("scope", str(access.user().provinceId()));
        }
        if (view.equals("mine")) condition += " AND r.initiator_id=UUID_TO_BIN(:actor)";
        else condition += " AND r.status<>'PENDING'";
        String join = " JOIN revenue_entry e ON e.id=r.entry_id";
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM revenue_recognition_request r" + join + condition,
                        args,
                        Long.class);
        args.addValue("limit", size).addValue("offset", page * size);
        var values =
                jdbc.query(
                        REQUEST_SELECT
                                + join
                                + condition
                                + " ORDER BY r.created_at DESC,r.id LIMIT :limit OFFSET :offset",
                        args,
                        (r, n) -> request(r));
        return new Page<>(values, total, (int) ((total + size - 1) / size), page, size);
    }

    public List<Request> assigned(UUID actor) {
        return jdbc.query(
                REQUEST_SELECT
                        + " WHERE r.approver_id=UUID_TO_BIN(:id) AND r.status='PENDING' ORDER BY"
                        + " r.created_at,r.id LIMIT 1000",
                Map.of("id", str(actor)),
                (r, n) -> request(r));
    }

    public void submit(UUID id, Entry e, UUID actor, UUID approver, Recognition value, Instant at) {
        jdbc.update(
                """
INSERT INTO revenue_recognition_request(id,entry_id,initiator_id,approver_id,base_version,status,proposed,created_at)
VALUES(UUID_TO_BIN(:id),UUID_TO_BIN(:entry),UUID_TO_BIN(:actor),UUID_TO_BIN(:approver),:base,'PENDING',CAST(:value AS JSON),:at)
""",
                Map.of(
                        "id",
                        str(id),
                        "entry",
                        str(e.id()),
                        "actor",
                        str(actor),
                        "approver",
                        str(approver),
                        "base",
                        e.version() + 1,
                        "value",
                        encode(value),
                        "at",
                        Timestamp.from(at)));
    }

    public void finish(UUID request, String state, String opinion, Instant at) {
        jdbc.update(
                "UPDATE revenue_recognition_request SET"
                    + " status=:state,opinion=:opinion,version_no=version_no+1,decided_at=:at WHERE"
                    + " id=UUID_TO_BIN(:id)",
                new MapSqlParameterSource("id", str(request))
                        .addValue("state", state)
                        .addValue("opinion", opinion)
                        .addValue("at", Timestamp.from(at)));
    }

    public List<History> history(UUID entry) {
        return jdbc.query(
                """
SELECT h.*,BIN_TO_UUID(h.id) hid,BIN_TO_UUID(h.actor_id) actor,BIN_TO_UUID(h.request_id) request,a.display_name actor_name
FROM revenue_recognition_history h JOIN identity_user_account a ON a.id=h.actor_id WHERE h.entry_id=UUID_TO_BIN(:id) ORDER BY h.occurred_at DESC,h.id
""",
                Map.of("id", str(entry)),
                (r, n) ->
                        new History(
                                uuid(r.getString("hid")),
                                uuid(r.getString("actor")),
                                r.getString("actor_name"),
                                uuid(r.getString("request")),
                                decode(r.getString("before_value"), Recognition.class),
                                decode(r.getString("after_value"), Recognition.class),
                                r.getTimestamp("occurred_at").toInstant()));
    }

    public void notify(
            UUID recipient, UUID entry, UUID request, String kind, String text, Instant at) {
        jdbc.update(
                "INSERT INTO"
                    + " revenue_notification(id,entry_id,recipient_id,request_id,kind,message,created_at)"
                    + " VALUES(UUID_TO_BIN(:id),UUID_TO_BIN(:entry),UUID_TO_BIN(:recipient),UUID_TO_BIN(:request),:kind,:message,:at)",
                new MapSqlParameterSource("id", str(UUID.randomUUID()))
                        .addValue("entry", str(entry))
                        .addValue("recipient", str(recipient))
                        .addValue("request", str(request))
                        .addValue("kind", kind)
                        .addValue("message", text)
                        .addValue("at", Timestamp.from(at)));
    }

    public List<Notice> notices(UUID actor) {
        return jdbc.query(
                "SELECT *,BIN_TO_UUID(id) nid,BIN_TO_UUID(entry_id) entry,BIN_TO_UUID(request_id)"
                    + " request FROM revenue_notification WHERE recipient_id=UUID_TO_BIN(:id) ORDER"
                    + " BY created_at DESC,id LIMIT 500",
                Map.of("id", str(actor)),
                (r, n) ->
                        new Notice(
                                uuid(r.getString("nid")),
                                uuid(r.getString("entry")),
                                uuid(r.getString("request")),
                                r.getString("kind"),
                                r.getString("message"),
                                r.getTimestamp("created_at").toInstant(),
                                instant(r, "read_at")));
    }

    public void read(UUID actor, UUID notice, Instant at) {
        jdbc.update(
                "UPDATE revenue_notification SET read_at=COALESCE(read_at,:at) WHERE"
                        + " id=UUID_TO_BIN(:id) AND recipient_id=UUID_TO_BIN(:actor)",
                Map.of("id", str(notice), "actor", str(actor), "at", Timestamp.from(at)));
    }

    public List<UUID> pendingEntries(UUID province) {
        return jdbc.query(
                "SELECT BIN_TO_UUID(e.id) FROM revenue_entry e JOIN revenue_recognition_request r"
                        + " ON r.entry_id=e.id AND r.status='PENDING' WHERE"
                        + " e.province_id=UUID_TO_BIN(:province) ORDER BY e.id",
                Map.of("province", str(province)),
                (r, n) -> uuid(r.getString(1)));
    }

    public void reassign(UUID request, UUID target) {
        jdbc.update(
                "UPDATE revenue_recognition_request SET"
                        + " approver_id=UUID_TO_BIN(:target),version_no=version_no+1 WHERE"
                        + " id=UUID_TO_BIN(:id)",
                Map.of("id", str(request), "target", str(target)));
    }

    public record Counts(long unread, long pending) {}

    public Counts counts(Access access) {
        var parameters = new MapSqlParameterSource("actor", str(access.user().id()));
        String scope = "";
        if (!access.national()) {
            scope = " AND e.province_id=UUID_TO_BIN(:province) AND e.specific_organization=FALSE";
            parameters.addValue("province", str(access.user().provinceId()));
        }
        long unread =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM revenue_notification n JOIN revenue_entry e ON"
                                + " e.id=n.entry_id WHERE n.recipient_id=UUID_TO_BIN(:actor) AND"
                                + " n.read_at IS NULL"
                                + scope,
                        parameters,
                        Long.class);
        long pending = 0;
        if (access.reviewer() && !access.user().reviewerProvinceIds().isEmpty()) {
            var ids =
                    access.user().reviewerProvinceIds().stream()
                            .map(
                                    id ->
                                            java.nio.ByteBuffer.allocate(16)
                                                    .putLong(id.getMostSignificantBits())
                                                    .putLong(id.getLeastSignificantBits())
                                                    .array())
                            .toList();
            parameters.addValue("provinces", ids);
            pending =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM revenue_recognition_request r JOIN revenue_entry"
                                + " e ON e.id=r.entry_id WHERE r.approver_id=UUID_TO_BIN(:actor)"
                                + " AND r.status='PENDING' AND e.province_id IN (:provinces)",
                            parameters,
                            Long.class);
        }
        return new Counts(unread, pending);
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        if (value == null) return null;
        try {
            return json.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> decodeMap(String value) {
        try {
            return json.readValue(
                    value,
                    json.getTypeFactory()
                            .constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static String str(UUID value) {
        return value == null ? null : value.toString();
    }

    private static Instant instant(ResultSet r, String column) throws SQLException {
        var value = r.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
