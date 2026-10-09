package com.alertamujer.backend.chat.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC access to the migrated, append-only emergency chat table. */
@Repository
public class ChatRepository {
    private final JdbcTemplate jdbc;

    public ChatRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<EmergencyData> findAuthorizedEmergency(UUID emergencyId, UUID actorUserId, String role, boolean lock) {
        String ownership = "USER".equals(role) ? " and emergency.user_id = ?" : "";
        List<EmergencyData> rows = jdbc.query("""
                select emergency.emergency_id, emergency.status
                  from emergency.emergencies emergency
                 where emergency.emergency_id = ?
                """ + ownership + (lock ? " for update" : ""), emergencyMapper(),
                "USER".equals(role) ? new Object[] {emergencyId, actorUserId} : new Object[] {emergencyId});
        return rows.stream().findFirst();
    }

    public Optional<ChatMessageData> findByClientMessageId(UUID emergencyId, UUID clientMessageId) {
        List<ChatMessageData> rows = jdbc.query("""
                select message.chat_message_id, message.client_message_id, message.sender_user_id,
                       sender.role, message.content, message.sent_at
                  from emergency.emergency_chat_messages message
                  join identity.users sender on sender.user_id = message.sender_user_id
                 where message.emergency_id = ? and message.client_message_id = ?
                """, messageMapper(), emergencyId, clientMessageId);
        return rows.stream().findFirst();
    }

    public ChatMessageData insert(UUID emergencyId, UUID senderUserId, UUID clientMessageId, String content, Instant sentAt) {
        return jdbc.queryForObject("""
                with inserted as (
                  insert into emergency.emergency_chat_messages (
                    client_message_id,
                    emergency_id,
                    sender_user_id,
                    content,
                    sent_at
                  )
                  values (?, ?, ?, ?, ?)
                  returning chat_message_id, client_message_id, sender_user_id, content, sent_at
                )
                select message.chat_message_id, message.client_message_id, message.sender_user_id,
                       sender.role, message.content, message.sent_at
                  from inserted message
                  join identity.users sender on sender.user_id = message.sender_user_id
                """, messageMapper(), clientMessageId, emergencyId, senderUserId, content, Timestamp.from(sentAt));
    }

    public List<ChatMessageData> findAfter(UUID emergencyId, long after, int size) {
        return jdbc.query("""
                select message.chat_message_id, message.client_message_id, message.sender_user_id,
                       sender.role, message.content, message.sent_at
                  from emergency.emergency_chat_messages message
                  join identity.users sender on sender.user_id = message.sender_user_id
                 where message.emergency_id = ? and message.chat_message_id > ?
                 order by message.chat_message_id
                 limit ?
                """, messageMapper(), emergencyId, after, size);
    }

    private static org.springframework.jdbc.core.RowMapper<EmergencyData> emergencyMapper() {
        return (rs, row) -> new EmergencyData(rs.getObject(1, UUID.class), rs.getString(2));
    }

    private static org.springframework.jdbc.core.RowMapper<ChatMessageData> messageMapper() {
        return (rs, row) -> new ChatMessageData(rs.getLong(1), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant());
    }

    public record EmergencyData(UUID id, String status) { }
    public record ChatMessageData(long id, UUID clientMessageId, UUID senderUserId,
            String senderRole, String content, Instant sentAt) { }
}
