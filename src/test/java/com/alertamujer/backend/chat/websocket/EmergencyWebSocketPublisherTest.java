package com.alertamujer.backend.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.alertamujer.backend.chat.dto.response.ChatMessageResponse;
import com.alertamujer.backend.chat.event.EmergencyMessageCreatedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

class EmergencyWebSocketPublisherTest {
    @Test
    void publishesTheAuthorizedSenderContextWithTheCommittedMessage() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        EmergencyWebSocketPublisher publisher = new EmergencyWebSocketPublisher(messaging);
        UUID emergencyId = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        UUID senderUserId = UUID.randomUUID();
        var message = new ChatMessageResponse(41L, clientMessageId, senderUserId, "ENTITY_ADMIN", "Help",
                Instant.parse("2026-10-09T18:00:00Z"));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        publisher.afterMessageCommit(new EmergencyMessageCreatedEvent(emergencyId, message));

        verify(messaging).convertAndSend(eq("/topic/emergencies/" + emergencyId), payload.capture());
        var json = new ObjectMapper().valueToTree(payload.getValue());
        assertThat(json.get("senderUserId").asText()).isEqualTo(senderUserId.toString());
        assertThat(json.get("senderRole").asText()).isEqualTo("ENTITY_ADMIN");
    }
}
