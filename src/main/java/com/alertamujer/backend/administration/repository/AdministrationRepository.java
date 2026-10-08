package com.alertamujer.backend.administration.repository;

import com.alertamujer.backend.emergency.repository.EmergencyRepository.EmergencyData;
import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.repository.ProfileRepository.UserProfileData;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Queries limited to the data needed for authorized administrative operation. */
@Repository
public class AdministrationRepository {
    private final JdbcTemplate jdbc;

    public AdministrationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isEnabledAdministrator(UUID userId) {
        Boolean result = jdbc.queryForObject("""
                select exists (
                    select 1 from identity.users
                     where user_id = ? and role = 'ENTITY_ADMIN' and account_status = 'ENABLED'
                )
                """, Boolean.class, userId);
        return Boolean.TRUE.equals(result);
    }

    public DashboardData dashboard() {
        return jdbc.queryForObject("""
                select count(*) filter (where status = 'ACTIVE'),
                       count(*) filter (where status = 'IN_PROGRESS'),
                       count(*) filter (where status = 'OFFLINE')
                  from emergency.emergencies
                """, (rs, row) -> new DashboardData(rs.getLong(1), rs.getLong(2), rs.getLong(3)));
    }

    public List<EmergencyData> findEmergencies(String status, int size, int offset) {
        return jdbc.query("""
                select emergency_id, status, previous_operational_status, started_at, last_heartbeat_at, finalized_at
                  from emergency.emergencies
                 where (? is null or status = ?)
                 order by started_at desc, emergency_id desc
                 limit ? offset ?
                """, (rs, row) -> new EmergencyData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                instant(rs.getTimestamp(4)), instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6))),
                status, status, size, offset);
    }

    public long countEmergencies(String status) {
        Long count = jdbc.queryForObject("select count(*) from emergency.emergencies where (? is null or status = ?)",
                Long.class, status, status);
        return count == null ? 0 : count;
    }

    public Optional<UUID> findEmergencyOwner(UUID emergencyId) {
        List<UUID> owners = jdbc.query("select user_id from emergency.emergencies where emergency_id = ?",
                (rs, row) -> rs.getObject(1, UUID.class), emergencyId);
        return owners.stream().findFirst();
    }

    public List<UserProfileData> findUsers(int size, int offset) {
        return jdbc.query("""
                select user_id, username::text, first_names, last_names, email::text, phone, role, account_status,
                       account_origin, accepted_terms_at
                  from identity.users
                 order by created_at desc, user_id desc
                 limit ? offset ?
                """, (rs, row) -> user(rs), size, offset);
    }

    public long countUsers() {
        Long count = jdbc.queryForObject("select count(*) from identity.users", Long.class);
        return count == null ? 0 : count;
    }

    public Optional<UserProfileData> findUser(UUID userId) {
        List<UserProfileData> users = jdbc.query("""
                select user_id, username::text, first_names, last_names, email::text, phone, role, account_status,
                       account_origin, accepted_terms_at
                  from identity.users where user_id = ?
                """, (rs, row) -> user(rs), userId);
        return users.stream().findFirst();
    }

    public Optional<ManagedUserData> lockManagedUser(UUID userId) {
        List<ManagedUserData> users = jdbc.query("""
                select user_id, role, account_status, disabled_at, last_activity_at, last_login_at, created_at
                  from identity.users where user_id = ? for update
                """, (rs, row) -> new ManagedUserData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                instant(rs.getTimestamp(4)), instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6)),
                instant(rs.getTimestamp(7))), userId);
        return users.stream().findFirst();
    }

    public void updateAccountStatus(UUID userId, String status, Instant now) {
        jdbc.update("""
                update identity.users
                   set account_status = ?, disabled_at = case when ? = 'DISABLED' then ?::timestamptz else null end, updated_at = ?
                where user_id = ?
                """, status, status, Timestamp.from(now), Timestamp.from(now), userId);
    }

    public List<UUID> findInactiveEnabledUserIds(Instant cutoff, int limit) {
        return jdbc.query("""
                select user_id
                  from identity.users
                 where role = 'USER'
                   and account_status = 'ENABLED'
                   and coalesce(last_activity_at, last_login_at, created_at) <= ?
                 order by user_id
                 limit ?
                """, (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(cutoff), limit);
    }

    /** The eligibility predicate is repeated in the update to prevent a stale job selection from disabling a fresh login. */
    public boolean disableUserIfStillInactive(UUID userId, Instant cutoff, Instant now) {
        return jdbc.update("""
                update identity.users
                   set account_status = 'DISABLED',
                       disabled_at = ?,
                       updated_at = ?
                 where user_id = ?
                   and role = 'USER'
                   and account_status = 'ENABLED'
                   and coalesce(last_activity_at, last_login_at, created_at) <= ?
                """, Timestamp.from(now), Timestamp.from(now), userId, Timestamp.from(cutoff)) == 1;
    }

    public void revokeAllSessions(UUID userId, Instant now) {
        jdbc.update("update identity.user_sessions set revoked_at = coalesce(revoked_at, ?) where user_id = ?",
                Timestamp.from(now), userId);
    }

    public Optional<ReplacementUserData> lockCurrentAdministrator() {
        List<ReplacementUserData> users = jdbc.query("""
                select user_id, role, account_status, accepted_terms_at
                  from identity.users where role = 'ENTITY_ADMIN' for update
                """, (rs, row) -> new ReplacementUserData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                instant(rs.getTimestamp(4))));
        return users.stream().findFirst();
    }

    public Optional<ReplacementUserData> lockReplacementTarget(UUID userId) {
        List<ReplacementUserData> users = jdbc.query("""
                select user_id, role, account_status, accepted_terms_at
                  from identity.users where user_id = ? for update
                """, (rs, row) -> new ReplacementUserData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                instant(rs.getTimestamp(4))), userId);
        return users.stream().findFirst();
    }

    public void replaceAdministrator(UUID outgoingUserId, UUID targetUserId, Instant now) {
        revokeAllSessions(outgoingUserId, now);
        jdbc.update("""
                update identity.users
                   set role = 'USER', account_status = 'DISABLED', disabled_at = ?, updated_at = ?
                 where user_id = ? and role = 'ENTITY_ADMIN'
                """, Timestamp.from(now), Timestamp.from(now), outgoingUserId);
        jdbc.update("""
                update identity.users
                   set role = 'ENTITY_ADMIN', account_status = 'ENABLED', disabled_at = null, updated_at = ?
                 where user_id = ? and role = 'USER' and account_status = 'ENABLED'
                """, Timestamp.from(now), targetUserId);
    }

    private UserProfileData user(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserProfileData(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                AccountOrigin.valueOf(rs.getString(9)), instant(rs.getTimestamp(10)));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record DashboardData(long activeCount, long inProgressCount, long offlineCount) { }
    public record ManagedUserData(UUID id, String role, String accountStatus, Instant disabledAt,
            Instant lastActivityAt, Instant lastLoginAt, Instant createdAt) { }
    public record ReplacementUserData(UUID id, String role, String accountStatus, Instant acceptedTermsAt) { }
}
