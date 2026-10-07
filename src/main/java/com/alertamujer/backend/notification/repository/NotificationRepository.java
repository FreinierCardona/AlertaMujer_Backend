package com.alertamujer.backend.notification.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL limited to notification tables and the contact/user data needed for the documented selection rule. */
@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isEnabledUser(UUID userId) {
        Boolean result = jdbc.queryForObject("""
                select exists (select 1 from identity.users
                 where user_id = ? and role = 'USER' and account_status = 'ENABLED')
                """, Boolean.class, userId);
        return Boolean.TRUE.equals(result);
    }

    public Optional<TokenOwner> findTokenOwner(String token) {
        List<TokenOwner> rows = jdbc.query("""
                select device_token_id, user_id from notification.user_device_tokens where fcm_token = ?
                """, (rs, row) -> new TokenOwner(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)), token);
        return rows.stream().findFirst();
    }

    public void insertToken(UUID tokenId, UUID userId, String token, String platform, Instant now) {
        jdbc.update("""
                insert into notification.user_device_tokens (device_token_id, user_id, fcm_token, platform, is_active,
                    last_seen_at, invalidated_at, created_at, updated_at)
                values (?, ?, ?, ?, true, ?, null, ?, ?)
                """, tokenId, userId, token, platform, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }

    public void refreshToken(UUID tokenId, String platform, Instant now) {
        jdbc.update("""
                update notification.user_device_tokens
                   set platform = ?, is_active = true, last_seen_at = ?, invalidated_at = null, updated_at = ?
                 where device_token_id = ?
                """, platform, Timestamp.from(now), Timestamp.from(now), tokenId);
    }

    public List<EligibleRecipient> findEligibleRecipients(UUID emergencyId) {
        return jdbc.query("""
                select contact.user_id, token.device_token_id, token.fcm_token
                  from emergency.emergencies emergency
                  join contacts.emergency_contacts relation on (relation.owner_user_id = emergency.user_id
                     or relation.contact_user_id = emergency.user_id) and relation.relationship_status = 'ACCEPTED'
                  join identity.users contact on contact.user_id = case when relation.owner_user_id = emergency.user_id
                     then relation.contact_user_id else relation.owner_user_id end
                  left join lateral (
                    select device_token_id, fcm_token from notification.user_device_tokens
                     where user_id = contact.user_id and is_active
                     order by last_seen_at desc nulls last, device_token_id desc limit 1
                  ) token on true
                 where emergency.emergency_id = ? and contact.role = 'USER' and contact.account_status = 'ENABLED'
                """, (rs, row) -> new EligibleRecipient(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3)), emergencyId);
    }

    public boolean insertAttempt(UUID attemptId, UUID emergencyId, EligibleRecipient recipient, String result, Instant now) {
        return jdbc.update("""
                insert into notification.emergency_notification_attempts (notification_attempt_id, emergency_id,
                    contact_user_id, device_token_id, result_status, attempted_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (emergency_id, contact_user_id) do nothing
                """, attemptId, emergencyId, recipient.contactUserId(), recipient.deviceTokenId(), result,
                Timestamp.from(now), Timestamp.from(now)) == 1;
    }

    public void completeAttempt(UUID attemptId, String result, String providerMessageId, String errorCode, Instant now) {
        jdbc.update("""
                update notification.emergency_notification_attempts
                   set result_status = ?, provider_message_id = ?, error_code = ?, updated_at = ?
                 where notification_attempt_id = ? and result_status = 'PENDING'
                """, result, providerMessageId, errorCode, Timestamp.from(now), attemptId);
    }

    public void invalidateToken(UUID tokenId, Instant now) {
        jdbc.update("""
                update notification.user_device_tokens
                   set is_active = false, invalidated_at = ?, updated_at = ?
                 where device_token_id = ? and is_active
                """, Timestamp.from(now), Timestamp.from(now), tokenId);
    }

    public record TokenOwner(UUID tokenId, UUID userId) { }
    public record EligibleRecipient(UUID contactUserId, UUID deviceTokenId, String fcmToken) { }
}
