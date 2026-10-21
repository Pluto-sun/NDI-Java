package com.pluto.ndi.shared.audit;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 审计只接收业务摘要，调用方不得传入密码或原始会话令牌。 */
@Service
public class AuditWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public AuditWriter(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String resourceType, UUID resourceId, String event, Map<String, ?> detail) {
        var now = Instant.now(clock);
        try {
            jdbc.update("""
                    INSERT INTO system_audit_event (id, actor_id, resource_type, resource_id, event_type, detail, occurred_at)
                    VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, CAST(? AS JSON), FROM_UNIXTIME(?))
                    """, UUID.randomUUID().toString(), actorId == null ? null : actorId.toString(), resourceType,
                    resourceId == null ? null : resourceId.toString(), event, json.writeValueAsString(detail),
                    BigDecimal.valueOf(now.getEpochSecond()).add(BigDecimal.valueOf(now.getNano(), 9)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("操作审计无法序列化", exception);
        }
    }
}
