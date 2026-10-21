package com.pluto.ndi.projects.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import com.pluto.ndi.identity.service.ProjectDataScope;
import com.pluto.ndi.identity.service.ProjectReminderAudience;
import com.pluto.ndi.projects.domain.ProjectInactivityCase;
import com.pluto.ndi.projects.domain.entity.ProjectEntity;
import com.pluto.ndi.projects.service.ProjectInactivityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 督办记录的查询和写入；调用方先锁定项目，统一并发顺序。 */
@Repository
public class ProjectInactivityMapper {
    private static final String COLUMNS = """
            BIN_TO_UUID(c.id) id, BIN_TO_UUID(c.project_id) project_id,
            p.project_code, p.project_name, BIN_TO_UUID(p.province_id) province_id,
            BIN_TO_UUID(p.city_id) city_id, c.activity_revision, c.last_activity_at, c.status,
            c.province_sent_at, c.interface_sent_at, c.group_sent_at, c.created_at, c.closed_at
            """;
    private static final String FROM = " FROM project_inactivity_case c JOIN project_project p ON p.id = c.project_id ";
    private static final String PENDING = """
            EXISTS (SELECT 1 FROM project_inactivity_notice n WHERE n.case_id = c.id
                AND n.recipient_id = UUID_TO_BIN(:actor) AND n.handled_at IS NULL)
            """;
    private static final String MINE = """
            WHERE EXISTS (SELECT 1 FROM project_inactivity_notice n WHERE n.case_id = c.id
                AND n.recipient_id = UUID_TO_BIN(:actor))
            AND (:level = 'NATIONAL' OR (:level = 'PROVINCE' AND p.province_id = UUID_TO_BIN(:scope))
                OR (:level = 'CITY' AND p.city_id = UUID_TO_BIN(:scope)))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ProjectInactivityMapper(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Candidate(UUID id, Instant lastActivityAt) { }
    public record Activity(long revision, Instant lastActivityAt) { }

    public void initialize(ProjectEntity project) {
        initialize(project, project.updatedAt());
    }

    public void initialize(ProjectEntity project, Instant lastActivityAt) {
        jdbc.update("""
                INSERT INTO project_activity_state (project_id, revision, last_activity_at)
                VALUES (UUID_TO_BIN(:project), 0, :at) ON DUPLICATE KEY UPDATE project_id = project_id
                """, new MapSqlParameterSource("project", project.id().toString()).addValue("at", utc(lastActivityAt)));
    }

    public Activity activity(UUID projectId, boolean locked) {
        return jdbc.queryForObject("""
                SELECT revision, last_activity_at FROM project_activity_state WHERE project_id = UUID_TO_BIN(:project)
                """ + (locked ? " FOR UPDATE" : ""), new MapSqlParameterSource("project", projectId.toString()),
                (row, number) -> new Activity(row.getLong("revision"), instant(row, "last_activity_at")));
    }

    public void recordActivity(UUID projectId, Instant at) {
        jdbc.update("""
                UPDATE project_activity_state SET revision = revision + 1, last_activity_at = :at
                WHERE project_id = UUID_TO_BIN(:project)
                """, new MapSqlParameterSource("project", projectId.toString()).addValue("at", utc(at)));
    }

    public List<Candidate> candidates(Instant cutoff, Instant cursorAt, UUID cursorId, int limit) {
        return jdbc.query("""
                SELECT BIN_TO_UUID(a.project_id) id, a.last_activity_at FROM project_activity_state a
                JOIN project_project p ON p.id = a.project_id
                WHERE p.current_stage_code <> 'TERMINATED' AND a.last_activity_at <= :cutoff
                  AND (a.last_activity_at > :cursorAt OR (a.last_activity_at = :cursorAt AND a.project_id > UUID_TO_BIN(:cursorId)))
                ORDER BY a.last_activity_at, a.project_id LIMIT :limit
                """, new MapSqlParameterSource("cutoff", utc(cutoff))
                .addValue("cursorAt", utc(cursorAt)).addValue("cursorId", cursorId.toString())
                .addValue("limit", limit), (row, number) -> new Candidate(uuid(row, "id"), instant(row, "last_activity_at")));
    }

    public Optional<ProjectInactivityCase> find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + ", false pending" + FROM + "WHERE c.id = UUID_TO_BIN(:id)",
                new MapSqlParameterSource("id", id.toString()), this::mapCase).stream().findFirst();
    }

    public Optional<ProjectInactivityCase> findLocked(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + ", false pending" + FROM + "WHERE c.id = UUID_TO_BIN(:id) FOR UPDATE",
                new MapSqlParameterSource("id", id.toString()), this::mapCase).stream().findFirst();
    }

    public Optional<ProjectInactivityCase> findCycle(UUID projectId, long revision) {
        return jdbc.query("SELECT " + COLUMNS + ", false pending" + FROM
                        + "WHERE c.project_id = UUID_TO_BIN(:project) AND c.activity_revision = :revision FOR UPDATE",
                new MapSqlParameterSource("project", projectId.toString()).addValue("revision", revision),
                this::mapCase).stream().findFirst();
    }

    public UUID open(ProjectEntity project, Activity activity, Instant at) {
        var id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO project_inactivity_case (id, project_id, activity_revision, last_activity_at, status, created_at, updated_at)
                VALUES (UUID_TO_BIN(:id), UUID_TO_BIN(:project), :revision, :lastAt, 'OPEN', :at, :at)
                """, new MapSqlParameterSource("id", id.toString()).addValue("project", project.id().toString())
                .addValue("revision", activity.revision()).addValue("lastAt", utc(activity.lastActivityAt()))
                .addValue("at", utc(at)));
        return id;
    }

    public int notice(UUID caseId, UUID recipientId, String kind, Instant at, boolean reopen) {
        var duplicate = reopen ? "handled_at = NULL, created_at = VALUES(created_at)" : "id = id";
        return jdbc.update("""
                INSERT INTO project_inactivity_notice (id, case_id, recipient_id, kind, created_at)
                VALUES (UUID_TO_BIN(:id), UUID_TO_BIN(:case), UUID_TO_BIN(:recipient), :kind, :at)
                ON DUPLICATE KEY UPDATE
                """ + duplicate, new MapSqlParameterSource("id", UUID.randomUUID().toString())
                .addValue("case", caseId.toString()).addValue("recipient", recipientId.toString())
                .addValue("kind", kind).addValue("at", utc(at)));
    }

    public void escalated(UUID id, ProjectReminderAudience audience, Instant at) {
        var column = switch (audience) {
            case PROVINCE -> "province_sent_at";
            case INTERFACE -> "interface_sent_at";
            case GROUP -> "group_sent_at";
        };
        jdbc.update("UPDATE project_inactivity_case SET " + column + " = COALESCE(" + column
                        + ", :at), updated_at = GREATEST(updated_at, :at) WHERE id = UUID_TO_BIN(:id)",
                new MapSqlParameterSource("id", id.toString()).addValue("at", utc(at)));
    }

    public boolean hasPendingNotice(UUID caseId, UUID actorId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM project_inactivity_notice
                WHERE case_id = UUID_TO_BIN(:case) AND recipient_id = UUID_TO_BIN(:actor) AND handled_at IS NULL
                """, new MapSqlParameterSource("case", caseId.toString()).addValue("actor", actorId.toString()),
                Long.class) > 0;
    }

    public boolean lockPendingNotice(UUID caseId, UUID actorId) {
        return !jdbc.queryForList("""
                SELECT BIN_TO_UUID(id) FROM project_inactivity_notice
                WHERE case_id = UUID_TO_BIN(:case) AND recipient_id = UUID_TO_BIN(:actor)
                    AND handled_at IS NULL FOR UPDATE
                """, new MapSqlParameterSource("case", caseId.toString()).addValue("actor", actorId.toString()),
                String.class).isEmpty();
    }

    public void handle(UUID caseId, List<UUID> recipients, Instant at) {
        if (recipients.isEmpty()) return;
        jdbc.update("""
                UPDATE project_inactivity_notice SET handled_at = :at
                WHERE case_id = UUID_TO_BIN(:case) AND recipient_id IN (:recipients)
                  AND handled_at IS NULL
                """, new MapSqlParameterSource("case", caseId.toString()).addValue("at", utc(at))
                .addValue("recipients", recipients.stream().map(ProjectInactivityMapper::bytes).toList()));
    }

    public void dispatch(UUID caseId, UUID senderId, String senderName, UUID recipientId,
            String recipientName, String route, String note, Instant at) {
        jdbc.update("""
                INSERT INTO project_inactivity_dispatch
                    (id, case_id, sender_id, recipient_id, sender_name, recipient_name, route, note, created_at)
                VALUES (UUID_TO_BIN(:id), UUID_TO_BIN(:case), UUID_TO_BIN(:sender), UUID_TO_BIN(:recipient),
                    :senderName, :recipientName, :route, :note, :at)
                """, new MapSqlParameterSource("id", UUID.randomUUID().toString()).addValue("case", caseId.toString())
                .addValue("sender", senderId.toString()).addValue("recipient", recipientId.toString())
                .addValue("senderName", senderName).addValue("recipientName", recipientName)
                .addValue("route", route).addValue("note", note).addValue("at", utc(at)));
        jdbc.update("UPDATE project_inactivity_case SET updated_at = GREATEST(updated_at, :at) WHERE id = UUID_TO_BIN(:case)",
                new MapSqlParameterSource("case", caseId.toString()).addValue("at", utc(at)));
    }

    public void close(UUID projectId, UUID actorId, Instant at) {
        var parameters = new MapSqlParameterSource("project", projectId.toString())
                .addValue("actor", actorId.toString()).addValue("at", utc(at));
        jdbc.update("""
                UPDATE project_inactivity_notice n JOIN project_inactivity_case c ON c.id = n.case_id
                SET n.handled_at = :at WHERE c.project_id = UUID_TO_BIN(:project)
                    AND c.status = 'OPEN' AND n.handled_at IS NULL
                """, parameters);
        int closed = jdbc.update("""
                UPDATE project_inactivity_case SET status = 'RESOLVED', closed_at = :at, updated_at = :at, closed_by = UUID_TO_BIN(:actor)
                WHERE project_id = UUID_TO_BIN(:project) AND status = 'OPEN'
                """, parameters);
        if (closed > 0) audit(projectId, null, actorId, "INACTIVITY_RESOLVED", "项目发生变动，督办闭环", at);
    }

    public Page<ProjectInactivityCase> findMine(UUID actorId, ProjectDataScope scope, boolean history, Pageable page) {
        var parameters = new MapSqlParameterSource("actor", actorId.toString()).addValue("level", scope.level().name())
                .addValue("scope", scope.organizationId() == null ? null : scope.organizationId().toString())
                .addValue("limit", page.getPageSize()).addValue("offset", page.getOffset());
        var filter = MINE + (history ? " AND (c.status = 'RESOLVED' OR NOT " + PENDING + ")"
                : " AND c.status = 'OPEN' AND " + PENDING);
        long count = jdbc.queryForObject("SELECT COUNT(*)" + FROM + filter, parameters, Long.class);
        var rows = jdbc.query("SELECT " + COLUMNS + ", " + PENDING + " pending" + FROM + filter
                + " ORDER BY c.updated_at DESC, c.id DESC LIMIT :limit OFFSET :offset",
                parameters, this::mapCase);
        return new PageImpl<>(rows, page, count);
    }

    public Map<UUID, List<ProjectInactivityEvent>> dispatchEvents(List<UUID> caseIds) {
        var result = new HashMap<UUID, List<ProjectInactivityEvent>>();
        if (caseIds.isEmpty()) return result;
        jdbc.query("""
                SELECT BIN_TO_UUID(case_id) case_id, sender_name, recipient_name, route, note, created_at
                FROM project_inactivity_dispatch WHERE case_id IN (:ids) ORDER BY created_at, id
                """, new MapSqlParameterSource("ids", caseIds.stream().map(ProjectInactivityMapper::bytes).toList()), row -> {
                    var caseId = uuid(row, "case_id");
                    var summary = row.getString("sender_name") + " 转发给 " + row.getString("recipient_name")
                            + "：" + row.getString("note");
                    result.computeIfAbsent(caseId, ignored -> new ArrayList<>()).add(
                            new ProjectInactivityEvent(row.getString("route"), summary, instant(row, "created_at")));
                });
        return result;
    }

    public void audit(UUID projectId, UUID caseId, UUID actorId, String action, String note, Instant at) {
        jdbc.update("""
                INSERT INTO system_audit_event (id, actor_id, event_type, resource_type, resource_id, detail, occurred_at)
                VALUES (UUID_TO_BIN(:id), UUID_TO_BIN(:actor), :action, 'PROJECT', UUID_TO_BIN(:project),
                    JSON_OBJECT('caseId', :case, 'note', :note), FROM_UNIXTIME(:epoch))
                """, new MapSqlParameterSource("id", UUID.randomUUID().toString())
                .addValue("actor", actorId == null ? null : actorId.toString()).addValue("action", action)
                .addValue("project", projectId.toString()).addValue("case", caseId == null ? null : caseId.toString())
                .addValue("note", note).addValue("epoch", BigDecimal.valueOf(at.getEpochSecond())
                        .add(BigDecimal.valueOf(at.getNano(), 9))));
    }

    private ProjectInactivityCase mapCase(ResultSet row, int number) throws SQLException {
        return new ProjectInactivityCase(uuid(row, "id"), uuid(row, "project_id"), row.getString("project_code"),
                row.getString("project_name"), uuid(row, "province_id"), uuid(row, "city_id"),
                row.getLong("activity_revision"), instant(row, "last_activity_at"), row.getString("status"),
                instant(row, "province_sent_at"), instant(row, "interface_sent_at"), instant(row, "group_sent_at"),
                instant(row, "created_at"), instant(row, "closed_at"), row.getBoolean("pending"));
    }

    private static UUID uuid(ResultSet row, String column) throws SQLException {
        return UUID.fromString(row.getString(column));
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant at) {
        return LocalDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
