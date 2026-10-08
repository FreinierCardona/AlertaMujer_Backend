package com.alertamujer.backend.chat.dto.request;

import java.util.UUID;

/** Client-supplied idempotency key and content for one STOMP chat message. */
public record ChatMessageInput(UUID clientMessageId, String content) { }
