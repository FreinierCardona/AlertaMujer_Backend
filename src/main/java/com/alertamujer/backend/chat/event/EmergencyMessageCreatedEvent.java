package com.alertamujer.backend.chat.event;

import com.alertamujer.backend.chat.dto.response.ChatMessageResponse;
import java.util.UUID;

/** Published inside the chat transaction and delivered to STOMP only after commit. */
public record EmergencyMessageCreatedEvent(UUID emergencyId, ChatMessageResponse message) { }
