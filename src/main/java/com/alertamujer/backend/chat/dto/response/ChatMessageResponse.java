package com.alertamujer.backend.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Public persisted chat representation shared by REST recovery and STOMP.
 *
 * <p>The presentation context intentionally contains only the authorized sender
 * identifier and current role. It lets each participant distinguish its own
 * message from the other participant without exposing a name, email or phone.</p>
 */
public record ChatMessageResponse(long messageId, UUID clientMessageId, UUID senderUserId,
        String senderRole, String content, Instant sentAt) { }
