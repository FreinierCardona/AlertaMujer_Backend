package com.alertamujer.backend.chat.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.chat.dto.request.ChatMessageInput;
import com.alertamujer.backend.chat.event.EmergencyMessageCreatedEvent;
import com.alertamujer.backend.chat.repository.ChatRepository;
import com.alertamujer.backend.chat.repository.ChatRepository.ChatMessageData;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ChatServiceImplTest {
    private final Instant now = Instant.parse("2026-10-07T18:00:00Z");
    private ChatRepository repository;
    private ApplicationEventPublisher events;
    private ChatServiceImpl service;
    private UUID emergencyId;
    private AuthenticatedIdentity owner;

    @BeforeEach
    void setUp() {
        repository = mock(ChatRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new ChatServiceImpl(repository, Clock.fixed(now, ZoneOffset.UTC), events);
        emergencyId = UUID.randomUUID();
        owner = new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID(), "USER", false);
    }

    @Test
    void persistsNormalizedMessageThenSchedulesOnePostCommitPublication() {
        UUID clientMessageId = UUID.randomUUID();
        when(repository.findAuthorizedEmergency(emergencyId, owner.userId(), "USER", true))
                .thenReturn(Optional.of(new ChatRepository.EmergencyData(emergencyId, "ACTIVE")));
        when(repository.findByClientMessageId(emergencyId, clientMessageId)).thenReturn(Optional.empty());
        when(repository.insert(eq(emergencyId), eq(owner.userId()), eq(clientMessageId), eq("Help me"), eq(now)))
                .thenReturn(new ChatMessageData(41L, clientMessageId, "Help me", now));

        var message = service.send(owner, emergencyId, new ChatMessageInput(clientMessageId, "  Help me  "));

        assertThat(message.messageId()).isEqualTo(41L);
        assertThat(message.content()).isEqualTo("Help me");
        verify(events).publishEvent(new EmergencyMessageCreatedEvent(emergencyId, message));
    }

    @Test
    void recoversThePersistedMessageWithoutAnotherInsertOrBusinessEvent() {
        UUID clientMessageId = UUID.randomUUID();
        ChatMessageData persisted = new ChatMessageData(41L, clientMessageId, "Already saved", now);
        when(repository.findAuthorizedEmergency(emergencyId, owner.userId(), "USER", true))
                .thenReturn(Optional.of(new ChatRepository.EmergencyData(emergencyId, "ACTIVE")));
        when(repository.findByClientMessageId(emergencyId, clientMessageId)).thenReturn(Optional.of(persisted));

        assertThat(service.send(owner, emergencyId, new ChatMessageInput(clientMessageId, "Different retry")).messageId())
                .isEqualTo(41L);

        verify(repository, never()).insert(any(), any(), any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void rejectsOfflineAndBlankMessagesBeforeInsert() {
        when(repository.findAuthorizedEmergency(emergencyId, owner.userId(), "USER", true))
                .thenReturn(Optional.of(new ChatRepository.EmergencyData(emergencyId, "OFFLINE")));
        assertThatThrownBy(() -> service.send(owner, emergencyId, new ChatMessageInput(UUID.randomUUID(), "hello")))
                .isInstanceOf(StateConflictException.class);
        assertThatThrownBy(() -> service.send(owner, emergencyId, new ChatMessageInput(UUID.randomUUID(), "   ")))
                .isInstanceOf(RuleViolationException.class);
        verify(repository, never()).insert(any(), any(), any(), any(), any());
    }
}
