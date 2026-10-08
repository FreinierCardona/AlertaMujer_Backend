package com.alertamujer.backend.shared.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC access is append/read only, matching the grants of {@code alertamujer_app}. */
@Repository
public class AuditRepository {
    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(AuditEvent event, String previousState, String newState, Instant now) {
        jdbc.update("""
                insert into audit.audit_logs (audit_log_id, actor_user_id, subject_user_id, action, entity_type,
                    entity_id, result, previous_state, new_state, description, created_at)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)
                """, UUID.randomUUID(), event.actorUserId(), event.subjectUserId(), event.action(), event.entityType(),
                event.entityId(), event.result(), previousState, newState, event.description(), Timestamp.from(now));
    }

    public List<AuditLogData> findPage(int size, int offset) {
        return jdbc.query("""
                select audit_log_id, action, created_at
                  from audit.audit_logs
                 order by created_at desc, audit_log_id desc
                 limit ? offset ?
                """, (rs, row) -> new AuditLogData(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getTimestamp(3).toInstant()), size, offset);
    }

    public long count() {
        Long count = jdbc.queryForObject("select count(*) from audit.audit_logs", Long.class);
        return count == null ? 0 : count;
    }

    public record AuditLogData(UUID id, String action, Instant createdAt) { }
}
