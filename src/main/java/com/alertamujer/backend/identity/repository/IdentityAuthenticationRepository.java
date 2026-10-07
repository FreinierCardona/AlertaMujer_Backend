package com.alertamujer.backend.identity.repository;

import com.alertamujer.backend.identity.model.AccountOrigin;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Authentication queries use only the columns granted to {@code alertamujer_app}.
 * Password and refresh hashes are read exclusively through PostgreSQL's scoped functions.
 */
@Repository
public class IdentityAuthenticationRepository {

    private final JdbcTemplate jdbc;

    public IdentityAuthenticationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Login: load only public account state, then read the password through its protected function.
    public Optional<UserAccount> findUserByIdentifier(String identifier) {
        return single("""
                select user_id,
                       username::text,
                       first_names,
                       last_names,
                       email::text,
                       phone,
                       role,
                       account_status,
                       account_origin,
                       accepted_terms_at
                  from identity.users
                 where username = ?
                    or email = ?
                """, this::mapUser, identifier, identifier);
    }

    public Optional<UserAccount> lockUserByEmail(String email) {
        return single("""
                select user_id,
                       username::text,
                       first_names,
                       last_names,
                       email::text,
                       phone,
                       role,
                       account_status,
                       account_origin,
                       accepted_terms_at
                  from identity.users
                 where email = ?
                 for update
                """, this::mapUser, email);
    }

    public Optional<UserAccount> lockUser(UUID userId) {
        return single("""
                select user_id,
                       username::text,
                       first_names,
                       last_names,
                       email::text,
                       phone,
                       role,
                       account_status,
                       account_origin,
                       accepted_terms_at
                  from identity.users
                 where user_id = ?
                 for update
                """, this::mapUser, userId);
    }

    public String readPasswordHash(UUID userId) {
        return jdbc.queryForObject(
                "select identity.get_user_password_hash(?)",
                String.class,
                userId);
    }

    // Session lifecycle: the clear refresh credential never appears in a table query.
    public void createSession(UUID sessionId, UUID userId, String refreshTokenHash, String clientType,
            Instant expiresAt, Instant now) {
        jdbc.update("""
                insert into identity.user_sessions (
                    session_id,
                    user_id,
                    refresh_token_hash,
                    client_type,
                    last_used_at,
                    expires_at,
                    created_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """, sessionId, userId, refreshTokenHash, clientType,
                Timestamp.from(now), Timestamp.from(expiresAt), Timestamp.from(now));
    }

    public Optional<SessionData> lockSession(UUID sessionId) {
        return single("""
                select session_id,
                       user_id,
                       client_type,
                       last_used_at,
                       expires_at,
                       revoked_at
                  from identity.user_sessions
                 where session_id = ?
                 for update
                """, (rs, row) -> new SessionData(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        instant(rs.getTimestamp(4)),
                        instant(rs.getTimestamp(5)),
                        instant(rs.getTimestamp(6))), sessionId);
    }

    public String readRefreshTokenHash(UUID sessionId) {
        return jdbc.queryForObject(
                "select identity.get_user_session_refresh_token_hash(?)",
                String.class,
                sessionId);
    }

    public void rotateSession(UUID sessionId, String refreshTokenHash, Instant expiresAt, Instant now) {
        jdbc.update("""
                update identity.user_sessions
                   set refresh_token_hash = ?,
                       last_used_at = ?,
                       expires_at = ?
                 where session_id = ?
                   and revoked_at is null
                """, refreshTokenHash, Timestamp.from(now), Timestamp.from(expiresAt), sessionId);
    }

    public void touchSessionAndUser(UUID sessionId, UUID userId, Instant now) {
        jdbc.update("""
                update identity.user_sessions
                   set last_used_at = ?
                 where session_id = ?
                   and revoked_at is null
                """,
                Timestamp.from(now), sessionId);
        jdbc.update("""
                update identity.users
                   set last_activity_at = ?,
                       updated_at = ?
                 where user_id = ?
                """,
                Timestamp.from(now), Timestamp.from(now), userId);
    }

    public void markLogin(UUID userId, Instant now) {
        jdbc.update("""
                update identity.users
                   set last_login_at = ?,
                       last_activity_at = ?,
                       updated_at = ?
                 where user_id = ?
                """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), userId);
    }

    public void revokeSession(UUID sessionId, Instant now) {
        jdbc.update("""
                update identity.user_sessions
                   set revoked_at = coalesce(revoked_at, ?)
                 where session_id = ?
                """, Timestamp.from(now), sessionId);
    }

    public void revokeAllSessions(UUID userId, Instant now) {
        jdbc.update("""
                update identity.user_sessions
                   set revoked_at = coalesce(revoked_at, ?)
                 where user_id = ?
                """, Timestamp.from(now), userId);
    }

    // Password change and recovery: obtain both secrets only from scoped database functions.
    public void updatePassword(UUID userId, String passwordHash, Instant now) {
        jdbc.update("""
                update identity.user_credentials
                   set password_hash = ?,
                       password_updated_at = ?,
                       updated_at = ?
                 where user_id = ?
                """, passwordHash, Timestamp.from(now), Timestamp.from(now), userId);
    }

    public Optional<PasswordResetCode> lockLatestPasswordResetCode(UUID userId, String email) {
        return single("""
                select verification_code_id,
                       expires_at,
                       used_at,
                       invalidated_at,
                       attempt_count,
                       max_attempts
                  from identity.user_verification_codes
                 where user_id = ?
                   and purpose = 'PASSWORD_RESET'
                   and channel = 'EMAIL'
                   and destination_snapshot = ?
                 order by created_at desc
                 limit 1
                 for update
                """, (rs, row) -> new PasswordResetCode(
                        rs.getObject(1, UUID.class),
                        instant(rs.getTimestamp(2)),
                        instant(rs.getTimestamp(3)),
                        instant(rs.getTimestamp(4)),
                        rs.getInt(5),
                        rs.getInt(6)), userId, email);
    }

    public String readVerificationCodeHash(UUID verificationCodeId) {
        return jdbc.queryForObject(
                "select identity.get_verification_code_hash(?)",
                String.class,
                verificationCodeId);
    }

    public void markVerificationCodeUsed(UUID verificationCodeId, Instant now) {
        jdbc.update("""
                update identity.user_verification_codes
                   set used_at = ?,
                       updated_at = ?
                 where verification_code_id = ?
                """, Timestamp.from(now), Timestamp.from(now), verificationCodeId);
    }

    public void recordFailedVerificationAttempt(UUID verificationCodeId, Instant now) {
        jdbc.update("""
                update identity.user_verification_codes
                   set attempt_count = attempt_count + 1,
                       invalidated_at = case
                           when attempt_count + 1 >= max_attempts then ?
                           else invalidated_at
                       end,
                       updated_at = ?
                 where verification_code_id = ?
                """, Timestamp.from(now), Timestamp.from(now), verificationCodeId);
    }

    private UserAccount mapUser(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new UserAccount(rs.getObject(1, UUID.class),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getString(8),
                AccountOrigin.valueOf(rs.getString(9)), instant(rs.getTimestamp(10)));
    }

    private <T> Optional<T> single(String sql, RowMapper<T> mapper, Object... args) {
        List<T> rows = jdbc.query(sql, mapper, args);
        return rows.stream().findFirst();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record UserAccount(UUID id, String username, String firstNames, String lastNames, String email,
            String phone, String role, String accountStatus, AccountOrigin accountOrigin, Instant acceptedTermsAt) {
        public boolean isEnabled() {
            return "ENABLED".equals(accountStatus);
        }

        public boolean hasPendingTerms() {
            return accountOrigin == AccountOrigin.ADMIN_CREATED && acceptedTermsAt == null;
        }
    }

    public record SessionData(UUID id, UUID userId, String clientType, Instant lastUsedAt, Instant expiresAt,
            Instant revokedAt) {
    }

    public record PasswordResetCode(UUID id, Instant expiresAt, Instant usedAt, Instant invalidatedAt,
            int attemptCount, int maxAttempts) {
    }
}
