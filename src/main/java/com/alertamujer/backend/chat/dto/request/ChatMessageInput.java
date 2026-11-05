package com.alertamujer.backend.chat.dto.request;

import java.util.UUID;

/** Clave de idempotencia y contenido enviados por el cliente para un mensaje de chat STOMP. */
public record ChatMessageInput(UUID clientMessageId, String content) { }
