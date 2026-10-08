package com.alertamujer.backend.chat.service.impl;

import com.alertamujer.backend.chat.dto.request.ChatMessageInput;
import com.alertamujer.backend.chat.dto.response.ChatMessageResponse;
import com.alertamujer.backend.chat.event.EmergencyMessageCreatedEvent;
import com.alertamujer.backend.chat.repository.ChatRepository;
import com.alertamujer.backend.chat.repository.ChatRepository.ChatMessageData;
import com.alertamujer.backend.chat.service.ChatService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Locks the emergency root before an append, so idempotency and status are revalidated atomically. */
@Service
class ChatServiceImpl implements ChatService {
    private final ChatRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    ChatServiceImpl(ChatRepository repository, ApplicationEventPublisher eventPublisher) {
        this(repository, Clock.systemUTC(), eventPublisher);
    }

    ChatServiceImpl(ChatRepository repository, Clock clock, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> list(AuthenticatedIdentity identity, UUID emergencyId, long after, int size) {
        requireReader(identity);
        if (after < 0 || size < 1 || size > 50) throw new RuleViolationException();
        authorize(identity, emergencyId, false);
        return repository.findAfter(emergencyId, after, size).stream().map(this::response).toList();
    }

    @Override
    @Transactional
    public ChatMessageResponse send(AuthenticatedIdentity identity, UUID emergencyId, ChatMessageInput input) {
        requireReader(identity);
        if (input == null || input.clientMessageId() == null) throw new RuleViolationException();
        String content = normalize(input.content());
        var emergency = authorize(identity, emergencyId, true);
        if (!isOperational(emergency.status())) throw new StateConflictException();

        ChatMessageData existing = repository.findByClientMessageId(emergencyId, input.clientMessageId()).orElse(null);
        if (existing != null) return response(existing);

        ChatMessageResponse message = response(repository.insert(emergencyId, identity.userId(), input.clientMessageId(), content,
                clock.instant()));
        eventPublisher.publishEvent(new EmergencyMessageCreatedEvent(emergencyId, message));
        return message;
    }

    @Override
    @Transactional(readOnly = true)
    public void authorizeSubscription(AuthenticatedIdentity identity, UUID emergencyId) {
        requireReader(identity);
        authorize(identity, emergencyId, false);
    }

    private ChatRepository.EmergencyData authorize(AuthenticatedIdentity identity, UUID emergencyId, boolean lock) {
        return repository.findAuthorizedEmergency(emergencyId, identity.userId(), identity.role(), lock)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private void requireReader(AuthenticatedIdentity identity) {
        if (identity == null || (!"USER".equals(identity.role()) && !"ENTITY_ADMIN".equals(identity.role()))) {
            throw new ForbiddenException();
        }
    }

    private String normalize(String content) {
        if (content == null) throw new RuleViolationException();
        String normalized = content.trim();
        if (normalized.isBlank() || normalized.length() > 500) throw new RuleViolationException();
        return normalized;
    }

    private boolean isOperational(String status) { return "ACTIVE".equals(status) || "IN_PROGRESS".equals(status); }

    private ChatMessageResponse response(ChatMessageData message) {
        return new ChatMessageResponse(message.id(), message.clientMessageId(), message.content(), message.sentAt());
    }
}
