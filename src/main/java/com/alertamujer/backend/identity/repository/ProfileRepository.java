package com.alertamujer.backend.identity.repository;

import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.model.OtpChannel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL limited to the deployed identity/profile grants used by HU-API-010. */
@Repository
public class ProfileRepository {
    private final JdbcTemplate jdbc;

    public ProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<UserProfileData> findUser(UUID userId) { return user(userId, false); }
    public Optional<UserProfileData> lockUser(UUID userId) { return user(userId, true); }

    public void updateProfile(UUID userId, String username, String firstNames, String lastNames, Instant now) {
        jdbc.update("""
                update identity.users set username = ?, first_names = ?, last_names = ?, updated_at = ? where user_id = ?
                """, username, firstNames, lastNames, Timestamp.from(now), userId);
    }

    public boolean anotherUserHasContact(String field, String value, UUID userId) {
        String column = "email".equals(field) ? "email" : "phone";
        Boolean found = jdbc.queryForObject("select exists (select 1 from identity.users where " + column
                + " = ? and user_id <> ?)", Boolean.class, value, userId);
        return Boolean.TRUE.equals(found);
    }

    public Optional<VerificationCodeData> lockLatestContactChangeCode(UUID userId, OtpChannel channel, String value) {
        List<VerificationCodeData> rows = jdbc.query("""
                select verification_code_id, expires_at, used_at, invalidated_at, attempt_count, max_attempts, resend_number, created_at
                  from identity.user_verification_codes
                 where user_id = ? and purpose = 'PROFILE_CONTACT_CHANGE' and channel = ? and destination_snapshot = ?
                 order by created_at desc limit 1 for update
                """, (rs, row) -> new VerificationCodeData(rs.getObject(1, UUID.class), instant(rs.getTimestamp(2)),
                instant(rs.getTimestamp(3)), instant(rs.getTimestamp(4)), rs.getInt(5), rs.getInt(6), rs.getInt(7),
                instant(rs.getTimestamp(8))), userId, channel.name(), value);
        return rows.stream().findFirst();
    }

    public void invalidateContactChangeCodes(UUID userId, OtpChannel channel, String value, Instant now) {
        jdbc.update("""
                update identity.user_verification_codes set invalidated_at = ?, updated_at = ?
                 where user_id = ? and purpose = 'PROFILE_CONTACT_CHANGE' and channel = ? and destination_snapshot = ?
                   and used_at is null and invalidated_at is null
                """, Timestamp.from(now), Timestamp.from(now), userId, channel.name(), value);
    }

    public void insertContactChangeCode(UUID codeId, UUID userId, OtpChannel channel, String value, String codeHash,
            Instant expiresAt, short maxAttempts, int resendNumber, Instant now) {
        jdbc.update("""
                insert into identity.user_verification_codes (verification_code_id, user_id, channel, purpose,
                    destination_snapshot, code_hash, expires_at, attempt_count, max_attempts, resend_number, created_at, updated_at)
                values (?, ?, ?, 'PROFILE_CONTACT_CHANGE', ?, ?, ?, 0, ?, ?, ?, ?)
                """, codeId, userId, channel.name(), value, codeHash, Timestamp.from(expiresAt), maxAttempts,
                resendNumber, Timestamp.from(now), Timestamp.from(now));
    }

    public String readVerificationCodeHash(UUID codeId) {
        return jdbc.queryForObject("select identity.get_verification_code_hash(?)", String.class, codeId);
    }
    public void recordFailedAttempt(UUID codeId, Instant now) {
        jdbc.update("""
                update identity.user_verification_codes set attempt_count = attempt_count + 1,
                    invalidated_at = case when attempt_count + 1 >= max_attempts then ? else invalidated_at end, updated_at = ?
                 where verification_code_id = ?
                """, Timestamp.from(now), Timestamp.from(now), codeId);
    }
    public void markCodeUsed(UUID codeId, Instant now) {
        jdbc.update("update identity.user_verification_codes set used_at = ?, updated_at = ? where verification_code_id = ?",
                Timestamp.from(now), Timestamp.from(now), codeId);
    }
    public void updateContact(UUID userId, String field, String value, Instant now) {
        String column = "email".equals(field) ? "email" : "phone";
        jdbc.update("update identity.users set " + column + " = ?, updated_at = ? where user_id = ?", value,
                Timestamp.from(now), userId);
    }
    public void revokeAllSessions(UUID userId, Instant now) {
        jdbc.update("update identity.user_sessions set revoked_at = coalesce(revoked_at, ?) where user_id = ?",
                Timestamp.from(now), userId);
    }
    public void acceptTerms(UUID userId, Instant now) {
        jdbc.update("update identity.users set accepted_terms_at = ?, updated_at = ? where user_id = ? and accepted_terms_at is null",
                Timestamp.from(now), Timestamp.from(now), userId);
    }
    public Optional<String> findEmergencyMessage(UUID userId) {
        List<String> rows = jdbc.query("select default_emergency_message from profile.user_emergency_settings where user_id = ?",
                (rs, row) -> rs.getString(1), userId);
        return rows.stream().findFirst();
    }
    public void upsertEmergencyMessage(UUID userId, String message, Instant now) {
        jdbc.update("""
                insert into profile.user_emergency_settings (setting_id, user_id, default_emergency_message, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                on conflict (user_id) do update set default_emergency_message = excluded.default_emergency_message,
                    updated_at = excluded.updated_at
                """, UUID.randomUUID(), userId, message, Timestamp.from(now), Timestamp.from(now));
    }
    public boolean hasOpenEmergency(UUID userId) {
        Boolean result = jdbc.queryForObject("select exists (select 1 from emergency.emergencies where user_id = ? and status <> 'FINALIZED')",
                Boolean.class, userId);
        return Boolean.TRUE.equals(result);
    }
    public List<String> findEvidenceReferences(UUID userId) {
        return jdbc.query("""
                select evidence.file_reference from emergency.emergency_evidences evidence
                 join emergency.emergencies emergency on emergency.emergency_id = evidence.emergency_id
                 where emergency.user_id = ?
                """, (rs, row) -> rs.getString(1), userId);
    }
    public void deleteUser(UUID userId) { jdbc.update("delete from identity.users where user_id = ?", userId); }

    public Optional<AdministrativeDeletionCandidate> lockAdministrativeDeletionCandidate(UUID userId) {
        List<AdministrativeDeletionCandidate> rows = jdbc.query("""
                select user_id, role, account_status, disabled_at
                  from identity.users where user_id = ? for update
                """, (rs, row) -> new AdministrativeDeletionCandidate(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), instant(rs.getTimestamp(4))), userId);
        return rows.stream().findFirst();
    }

    private Optional<UserProfileData> user(UUID userId, boolean lock) {
        List<UserProfileData> rows = jdbc.query("""
                select user_id, username::text, first_names, last_names, email::text, phone, role, account_status,
                       account_origin, accepted_terms_at
                  from identity.users where user_id = ?
                """ + (lock ? " for update" : ""), (rs, row) -> new UserProfileData(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getString(8), AccountOrigin.valueOf(rs.getString(9)), instant(rs.getTimestamp(10))), userId);
        return rows.stream().findFirst();
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }

    public record UserProfileData(UUID id, String username, String firstNames, String lastNames, String email,
            String phone, String role, String accountStatus, AccountOrigin accountOrigin, Instant acceptedTermsAt) { }
    public record VerificationCodeData(UUID id, Instant expiresAt, Instant usedAt, Instant invalidatedAt,
            int attemptCount, int maxAttempts, int resendNumber, Instant createdAt) { }
    public record AdministrativeDeletionCandidate(UUID id, String role, String accountStatus, Instant disabledAt) { }
}
