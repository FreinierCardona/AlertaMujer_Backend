package com.alertamujer.backend.chat.event;

import com.alertamujer.backend.chat.dto.response.ChatMessageResponse;
import java.util.UUID;

/** Se publica dentro de la transacción del chat y se entrega a STOMP solo después de confirmarla. */
public record EmergencyMessageCreatedEvent(UUID emergencyId, ChatMessageResponse message) { }
