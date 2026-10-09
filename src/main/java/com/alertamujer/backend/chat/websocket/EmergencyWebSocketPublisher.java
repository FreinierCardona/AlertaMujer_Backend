package com.alertamujer.backend.chat.websocket;

import com.alertamujer.backend.chat.event.EmergencyMessageCreatedEvent;
import com.alertamujer.backend.emergency.event.EmergencyStatusChangedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Emits only committed emergency events; STOMP transport never confirms an uncommitted write. */
@Component
class EmergencyWebSocketPublisher {
    private final SimpMessagingTemplate messaging;

    EmergencyWebSocketPublisher(SimpMessagingTemplate messaging) { this.messaging = messaging; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterMessageCommit(EmergencyMessageCreatedEvent event) {
        messaging.convertAndSend(topic(event.emergencyId()), new MessageEvent("EMERGENCY_MESSAGE_CREATED", event.emergencyId(),
                event.message().messageId(), event.message().clientMessageId(), event.message().senderUserId(),
                event.message().senderRole(), event.message().content(), event.message().sentAt()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterStatusCommit(EmergencyStatusChangedEvent event) {
        messaging.convertAndSend(topic(event.emergencyId()), new StatusEvent("EMERGENCY_STATUS_CHANGED", event.emergencyId(),
                event.status(), event.occurredAt()));
    }

    private String topic(UUID emergencyId) { return "/topic/emergencies/" + emergencyId; }

    private record MessageEvent(String type, UUID emergencyId, long messageId, UUID clientMessageId,
            UUID senderUserId, String senderRole, String content, Instant sentAt) { }
    private record StatusEvent(String type, UUID emergencyId, String status, Instant occurredAt) { }
}
