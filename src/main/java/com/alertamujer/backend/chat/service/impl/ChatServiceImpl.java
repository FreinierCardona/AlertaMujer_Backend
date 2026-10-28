package com.alertamujer.backend.chat.service.impl;

import com.alertamujer.backend.administration.service.AdministrationService;
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
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.audit.AuditEvent;
import com.alertamujer.backend.shared.audit.AuditService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bloquea la emergencia al enviar para validar su estado y evitar mensajes duplicados en una misma transacción. */
@Service
class ChatServiceImpl implements ChatService {
    private final ChatRepository repository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final SystemConfigurationValues configuration;
    private final AuditService auditService;
    private final AdministrationService administrationService;

    @Autowired
    ChatServiceImpl(ChatRepository repository, ApplicationEventPublisher eventPublisher,
            SystemConfigurationValues configuration, AuditService auditService, AdministrationService administrationService) {
        this(repository, Clock.systemUTC(), eventPublisher, configuration, auditService, administrationService);
    }

    ChatServiceImpl(ChatRepository repository, Clock clock, ApplicationEventPublisher eventPublisher,
            SystemConfigurationValues configuration, AuditService auditService, AdministrationService administrationService) {
        this.repository = repository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.configuration = configuration;
        this.auditService = auditService;
        this.administrationService = administrationService;
    }

    /** Consulta mensajes posteriores al cursor; registra las lecturas administrativas en auditoría. */
    @Override
    @Transactional
    public List<ChatMessageResponse> list(AuthenticatedIdentity identity, UUID emergencyId, long after, int size) {
        requireReader(identity);
        if (after < 0 || size < 1 || size > 50) throw new RuleViolationException();
        authorize(identity, emergencyId, false);
        List<ChatMessageResponse> messages = repository.findAfter(emergencyId, after, size).stream().map(this::response).toList();
        if ("ENTITY_ADMIN".equals(identity.role())) {
            auditService.record(AuditEvent.success(identity.userId(), null, "ALERT_VIEWED", "EMERGENCY", emergencyId,
                    null, null, "Administrative emergency messages viewed."));
        }
        return messages;
    }

    @Override
    @Transactional
    public ChatMessageResponse send(AuthenticatedIdentity identity, UUID emergencyId, ChatMessageInput input) {
        requireReader(identity);
        if (input == null || input.clientMessageId() == null) throw new RuleViolationException();
        String content = normalize(input.content());
        var emergency = authorize(identity, emergencyId, true);
        if (!isOperational(emergency.status())) throw new StateConflictException();

        // El identificador del cliente permite devolver el mismo mensaje cuando se reintenta el envío.
        ChatMessageData existing = repository.findByClientMessageId(emergencyId, input.clientMessageId()).orElse(null);
        if (existing != null) return response(existing);

        ChatMessageResponse message = response(repository.insert(emergencyId, identity.userId(), input.clientMessageId(), content,
                clock.instant()));
        // El primer mensaje nuevo del administrador inicia atención en la misma transacción.
        if ("ENTITY_ADMIN".equals(identity.role()) && "ACTIVE".equals(emergency.status())) {
            administrationService.startAttention(identity, emergencyId);
        }
        eventPublisher.publishEvent(new EmergencyMessageCreatedEvent(emergencyId, message));
        return message;
    }

    /** Aplica la autorización de lectura antes de permitir la suscripción al canal de la emergencia. */
    @Override
    @Transactional(readOnly = true)
    public void authorizeSubscription(AuthenticatedIdentity identity, UUID emergencyId) {
        requireReader(identity);
        authorize(identity, emergencyId, false);
    }

    // La consulta restringe por usuario y rol; lock solicita además el bloqueo para escritura.
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
        if (normalized.isBlank() || normalized.length() > configuration.maxChatMessageLength()) throw new RuleViolationException();
        return normalized;
    }

    private boolean isOperational(String status) {
        return "ACTIVE".equals(status) || "IN_PROGRESS".equals(status);
    }

    private ChatMessageResponse response(ChatMessageData message) {
        return new ChatMessageResponse(message.id(), message.clientMessageId(), message.senderUserId(),
                message.senderRole(), message.content(), message.sentAt());
    }
}
