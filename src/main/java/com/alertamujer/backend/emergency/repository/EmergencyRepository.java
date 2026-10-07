package com.alertamujer.backend.emergency.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL for the emergency tables already owned and migrated by the Database project. */
@Repository
public class EmergencyRepository {

    private final JdbcTemplate jdbc;

    public EmergencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserData> lockEnabledUser(UUID userId) {
        return enabledUser(userId, true);
    }

    public Optional<UserData> findEnabledUser(UUID userId) {
        return enabledUser(userId, false);
    }

    private Optional<UserData> enabledUser(UUID userId, boolean lock) {
        List<UserData> rows = jdbc.query("""
                select user_id from identity.users
                 where user_id = ? and role = 'USER' and account_status = 'ENABLED'
                """ + (lock ? " for update" : ""), (rs, row) -> new UserData(rs.getObject(1, UUID.class)), userId);
        return rows.stream().findFirst();
    }

    public boolean hasEligibleContact(UUID userId) {
        Boolean eligible = jdbc.queryForObject("""
                select exists (
                    select 1
                      from contacts.emergency_contacts relation
                      join identity.users contact on contact.user_id = case
                          when relation.owner_user_id = ? then relation.contact_user_id
                          else relation.owner_user_id
                      end
                     where (relation.owner_user_id = ? or relation.contact_user_id = ?)
                       and relation.relationship_status = 'ACCEPTED'
                       and contact.role = 'USER' and contact.account_status = 'ENABLED'
                )
                """, Boolean.class, userId, userId, userId);
        return Boolean.TRUE.equals(eligible);
    }

    public Optional<EmergencyData> findOpenEmergency(UUID userId, boolean lock) {
        List<EmergencyData> rows = jdbc.query("""
                select emergency_id, status, previous_operational_status, started_at, last_heartbeat_at, finalized_at
                  from emergency.emergencies
                 where user_id = ? and status <> 'FINALIZED'
                """ + (lock ? " for update" : ""), emergencyMapper(), userId);
        return rows.stream().findFirst();
    }

    public Optional<EmergencyData> findOwnEmergency(UUID emergencyId, UUID userId) {
        List<EmergencyData> rows = jdbc.query("""
                select emergency_id, status, previous_operational_status, started_at, last_heartbeat_at, finalized_at
                  from emergency.emergencies
                 where emergency_id = ? and user_id = ?
                """, emergencyMapper(), emergencyId, userId);
        return rows.stream().findFirst();
    }

    public boolean insertActiveEmergency(UUID emergencyId, UUID userId, String messageSnapshot, Instant now) {
        return jdbc.update("""
                insert into emergency.emergencies (emergency_id, user_id, status, message_snapshot, started_at, created_at, updated_at)
                values (?, ?, 'ACTIVE', ?, ?, ?, ?)
                on conflict (user_id) where status <> 'FINALIZED' do nothing
                """, emergencyId, userId, messageSnapshot, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    public void insertInitialLocation(UUID emergencyId, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
            Instant capturedAt, Instant receivedAt) {
        jdbc.update("""
                insert into emergency.emergency_locations (location_id, emergency_id, latitude, longitude, accuracy_meters,
                    captured_at, received_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), emergencyId, latitude, longitude, accuracyMeters, Timestamp.from(capturedAt),
                Timestamp.from(receivedAt));
    }

    public void insertInitialHistory(UUID emergencyId, UUID actorUserId, Instant occurredAt) {
        jdbc.update("""
                insert into emergency.emergency_status_history (emergency_status_history_id, emergency_id, sequence_no,
                    previous_status, new_status, actor_user_id, occurred_at)
                values (?, ?, 1, null, 'ACTIVE', ?, ?)
                """, UUID.randomUUID(), emergencyId, actorUserId, Timestamp.from(occurredAt));
    }

    public Optional<String> findValidEmergencyMessage(UUID userId) {
        List<String> rows = jdbc.query("""
                select default_emergency_message
                  from profile.user_emergency_settings
                 where user_id = ? and default_emergency_message is not null
                   and btrim(default_emergency_message) <> ''
                """, (rs, row) -> rs.getString(1), userId);
        return rows.stream().findFirst();
    }

    private static org.springframework.jdbc.core.RowMapper<EmergencyData> emergencyMapper() {
        return (rs, row) -> new EmergencyData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                instant(rs.getTimestamp(4)), instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6)));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record UserData(UUID id) { }
    public record EmergencyData(UUID id, String status, String previousOperationalStatus, Instant startedAt,
            Instant lastHeartbeatAt, Instant finalizedAt) { }
}
