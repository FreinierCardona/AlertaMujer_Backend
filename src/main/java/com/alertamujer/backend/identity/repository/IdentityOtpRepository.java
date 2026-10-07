package com.alertamujer.backend.identity.repository;

import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.model.OtpChannel;
import com.alertamujer.backend.identity.model.OtpPurpose;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Uses only the columns granted to alertamujer_app. OTP/password hashes are
 * retrieved by the narrowly scoped PostgreSQL functions, never table SELECT.
 */
@Repository
public class IdentityOtpRepository {

    private final JdbcTemplate jdbc;

    public IdentityOtpRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Registration context lookup and locking.
    public Optional<RegistrationRequestData> lockRegistrationRequest(UUID requestId) {
        return single("""
                select registration_request_id,
                       username::text,
                       first_names,
                       last_names,
                       email::text,
                       phone,
                       status,
                       expires_at,
                       account_origin,
                       accepted_terms_at
                  from identity.registration_requests
                 where registration_request_id = ?
                 for update
                """,
                (rs, row) -> new RegistrationRequestData(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7),
                        instant(rs.getTimestamp(8)),
                        AccountOrigin.valueOf(rs.getString(9)),
                        instant(rs.getTimestamp(10))),
                requestId);
    }

    public Optional<UserData> lockUserByEmail(String email) {
        return single("""
                select user_id, email::text
                  from identity.users
                 where email = ?
                 for update
                """,
                (rs, row) -> new UserData(
                        rs.getObject(1, UUID.class),
                        rs.getString(2)),
                email);
    }

    // OTP issuance and invalidation for registration or an existing account.
    public Optional<VerificationCodeData> lockLatestRegistrationCode(
            UUID requestId, OtpPurpose purpose, OtpChannel channel, String destination) {
        return lockLatestCode("registration_request_id = ?", requestId, purpose, channel, destination);
    }

    public Optional<VerificationCodeData> lockLatestUserCode(
            UUID userId, OtpPurpose purpose, OtpChannel channel, String destination) {
        return lockLatestCode("user_id = ?", userId, purpose, channel, destination);
    }

    public void invalidateActiveRegistrationCodes(
            UUID requestId,
            OtpPurpose purpose,
            OtpChannel channel,
            String destination,
            Instant now) {
        invalidateActive("registration_request_id = ?", requestId, purpose, channel, destination, now);
    }

    public void invalidateActiveUserCodes(
            UUID userId,
            OtpPurpose purpose,
            OtpChannel channel,
            String destination,
            Instant now) {
        invalidateActive("user_id = ?", userId, purpose, channel, destination, now);
    }

    public void insertRegistrationCode(
            UUID codeId,
            UUID requestId,
            OtpChannel channel,
            OtpPurpose purpose,
            String destination,
            String codeHash,
            Instant expiresAt,
            short maxAttempts,
            int resendNumber,
            Instant now) {
        insertCode(
                codeId,
                null,
                requestId,
                channel,
                purpose,
                destination,
                codeHash,
                expiresAt,
                maxAttempts,
                resendNumber,
                now);
    }

    public void insertUserCode(
            UUID codeId,
            UUID userId,
            OtpChannel channel,
            OtpPurpose purpose,
            String destination,
            String codeHash,
            Instant expiresAt,
            short maxAttempts,
            int resendNumber,
            Instant now) {
        insertCode(
                codeId,
                userId,
                null,
                channel,
                purpose,
                destination,
                codeHash,
                expiresAt,
                maxAttempts,
                resendNumber,
                now);
    }

    public String readVerificationCodeHash(UUID verificationCodeId) {
        return jdbc.queryForObject(
                "select identity.get_verification_code_hash(?)",
                String.class,
                verificationCodeId);
    }

    // OTP consumption and registration completion.
    public void recordFailedAttempt(UUID verificationCodeId, Instant now) {
        jdbc.update("""
                update identity.user_verification_codes
                   set attempt_count = attempt_count + 1,
                       invalidated_at = case
                           when attempt_count + 1 >= max_attempts then ?
                           else invalidated_at
                       end,
                       updated_at = ?
                 where verification_code_id = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                verificationCodeId);
    }

    public void markCodeUsed(UUID verificationCodeId, Instant now) {
        jdbc.update("""
                update identity.user_verification_codes
                   set used_at = ?,
                       updated_at = ?
                 where verification_code_id = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                verificationCodeId);
    }

    public boolean isRegistrationChannelVerified(UUID requestId, OtpPurpose purpose, OtpChannel channel) {
        Boolean result = jdbc.queryForObject("""
                select exists (
                    select 1
                      from identity.user_verification_codes
                     where registration_request_id = ?
                       and purpose = ?
                       and channel = ?
                       and used_at is not null
                )
                """,
                Boolean.class,
                requestId,
                purpose.name(),
                channel.name());
        return Boolean.TRUE.equals(result);
    }

    public String readRegistrationPasswordHash(UUID requestId) {
        return jdbc.queryForObject(
                "select identity.get_registration_password_hash(?)",
                String.class,
                requestId);
    }

    public UUID createUserAndCredential(
            RegistrationRequestData request,
            String passwordHash,
            Instant now) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                insert into identity.users (
                    user_id,
                    username,
                    first_names,
                    last_names,
                    email,
                    phone,
                    role,
                    account_status,
                    account_origin,
                    accepted_terms_at,
                    created_at,
                    updated_at
                ) values (
                    ?, ?, ?, ?, ?, ?, 'USER', 'ENABLED', ?, ?, ?, ?
                )
                """,
                userId,
                request.username(),
                request.firstNames(),
                request.lastNames(),
                request.email(),
                request.phone(),
                request.accountOrigin().name(),
                timestamp(request.acceptedTermsAt()),
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update("""
                insert into identity.user_credentials (
                    credential_id,
                    user_id,
                    password_hash,
                    password_updated_at,
                    created_at,
                    updated_at
                ) values (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                passwordHash,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now));
        return userId;
    }

    public void markRegistrationCompleted(UUID requestId, Instant now) {
        jdbc.update("""
                update identity.registration_requests
                   set status = 'COMPLETED',
                       updated_at = ?
                 where registration_request_id = ?
                   and status = 'PENDING'
                """,
                Timestamp.from(now),
                requestId);
    }

    public void markRegistrationExpired(UUID requestId, Instant now) {
        jdbc.update("""
                update identity.registration_requests
                   set status = 'EXPIRED',
                       updated_at = ?
                 where registration_request_id = ?
                   and status = 'PENDING'
                   and expires_at <= ?
                """,
                Timestamp.from(now),
                requestId,
                Timestamp.from(now));
    }

    // Scheduled cleanup; each statement selects a bounded, locked batch first.
    public int deleteInvalidVerificationCodes(Instant now, int limit) {
        return jdbc.update("""
                with candidates as (
                    select verification_code_id
                      from identity.user_verification_codes
                     where expires_at <= ?
                        or used_at is not null
                        or invalidated_at is not null
                        or attempt_count >= max_attempts
                     order by created_at
                     limit ?
                     for update skip locked
                )
                delete from identity.user_verification_codes codes
                 using candidates
                 where codes.verification_code_id = candidates.verification_code_id
                """,
                Timestamp.from(now),
                limit);
    }

    public int deleteStaleRegistrationRequests(Instant now, int limit) {
        return jdbc.update("""
                with candidates as (
                    select registration_request_id
                      from identity.registration_requests
                     where expires_at <= ?
                        or status in ('COMPLETED', 'CANCELLED', 'EXPIRED')
                     order by created_at
                     limit ?
                     for update skip locked
                )
                delete from identity.registration_requests requests
                 using candidates
                 where requests.registration_request_id = candidates.registration_request_id
                """,
                Timestamp.from(now),
                limit);
    }

    private Optional<VerificationCodeData> lockLatestCode(
            String contextColumn,
            UUID contextId,
            OtpPurpose purpose,
            OtpChannel channel,
            String destination) {
        return single("""
                select verification_code_id,
                       channel,
                       purpose,
                       destination_snapshot,
                       expires_at,
                       used_at,
                       invalidated_at,
                       attempt_count,
                       max_attempts,
                       resend_number,
                       created_at
                  from identity.user_verification_codes
                 where %s
                   and purpose = ?
                   and channel = ?
                   and destination_snapshot = ?
                 order by created_at desc
                 limit 1
                 for update
                """.formatted(contextColumn),
                (rs, row) -> new VerificationCodeData(
                        rs.getObject(1, UUID.class),
                        OtpChannel.valueOf(rs.getString(2)),
                        OtpPurpose.valueOf(rs.getString(3)),
                        rs.getString(4),
                        instant(rs.getTimestamp(5)),
                        instant(rs.getTimestamp(6)),
                        instant(rs.getTimestamp(7)),
                        rs.getInt(8),
                        rs.getInt(9),
                        rs.getInt(10),
                        instant(rs.getTimestamp(11))),
                contextId,
                purpose.name(),
                channel.name(),
                destination);
    }

    private void invalidateActive(
            String contextColumn,
            UUID contextId,
            OtpPurpose purpose,
            OtpChannel channel,
            String destination,
            Instant now) {
        jdbc.update(("""
                update identity.user_verification_codes
                   set invalidated_at = ?,
                       updated_at = ?
                 where %s
                   and purpose = ?
                   and channel = ?
                   and destination_snapshot = ?
                   and used_at is null
                   and invalidated_at is null
                """).formatted(contextColumn),
                Timestamp.from(now),
                Timestamp.from(now),
                contextId,
                purpose.name(),
                channel.name(),
                destination);
    }

    private void insertCode(
            UUID codeId,
            UUID userId,
            UUID requestId,
            OtpChannel channel,
            OtpPurpose purpose,
            String destination,
            String codeHash,
            Instant expiresAt,
            short maxAttempts,
            int resendNumber,
            Instant now) {
        jdbc.update("""
                insert into identity.user_verification_codes (
                    verification_code_id,
                    user_id,
                    registration_request_id,
                    channel,
                    purpose,
                    destination_snapshot,
                    code_hash,
                    expires_at,
                    attempt_count,
                    max_attempts,
                    resend_number,
                    created_at,
                    updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                """,
                codeId,
                userId,
                requestId,
                channel.name(),
                purpose.name(),
                destination,
                codeHash,
                Timestamp.from(expiresAt),
                maxAttempts,
                resendNumber,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private <T> Optional<T> single(
            String sql,
            RowMapper<T> mapper,
            Object... args) {
        List<T> rows = jdbc.query(sql, mapper, args);
        return rows.stream().findFirst();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    public record RegistrationRequestData(
            UUID id,
            String username,
            String firstNames,
            String lastNames,
            String email,
            String phone,
            String status,
            Instant expiresAt,
            AccountOrigin accountOrigin,
            Instant acceptedTermsAt) {
    }

    public record VerificationCodeData(
            UUID id,
            OtpChannel channel,
            OtpPurpose purpose,
            String destination,
            Instant expiresAt,
            Instant usedAt,
            Instant invalidatedAt,
            int attemptCount,
            int maxAttempts,
            int resendNumber,
            Instant createdAt) {
    }

    public record UserData(UUID id, String email) {
    }
}
