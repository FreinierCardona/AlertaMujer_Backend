package com.alertamujer.backend.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public, persisted chat message representation shared by REST and STOMP. */
public record ChatMessageResponse(long messageId, UUID clientMessageId, String content, Instant sentAt) { }
