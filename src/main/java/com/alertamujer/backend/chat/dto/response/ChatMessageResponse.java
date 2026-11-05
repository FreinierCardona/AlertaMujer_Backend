package com.alertamujer.backend.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Representación pública del mensaje persistido, compartida por la recuperación REST y STOMP. El
 * contexto de presentación contiene únicamente el identificador autorizado del remitente y su rol
 * actual. Permite distinguir los mensajes propios de los del otro participante sin exponer nombre,
 * correo ni teléfono.
 */
public record ChatMessageResponse(long messageId, UUID clientMessageId, UUID senderUserId,
        String senderRole, String content, Instant sentAt) { }
