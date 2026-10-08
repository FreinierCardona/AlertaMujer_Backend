package com.alertamujer.backend.emergency.service.impl;

import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.dto.request.LocationInput;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.emergency.event.EmergencyCreatedEvent;
import com.alertamujer.backend.emergency.event.EmergencyStatusChangedEvent;
import com.alertamujer.backend.emergency.repository.EmergencyRepository;
import com.alertamujer.backend.emergency.repository.EmergencyRepository.EmergencyData;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the SOS root, its first location and its initial history event atomic. */
@Service
class EmergencyServiceImpl implements EmergencyService {

    private final EmergencyRepository repository;
    private final SystemConfigurationValues configuration;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    EmergencyServiceImpl(EmergencyRepository repository, SystemConfigurationValues configuration,
            ApplicationEventPublisher eventPublisher) {
        this(repository, configuration, Clock.systemUTC(), eventPublisher);
    }

    EmergencyServiceImpl(EmergencyRepository repository, SystemConfigurationValues configuration, Clock clock) {
        this(repository, configuration, clock, event -> { });
    }

    EmergencyServiceImpl(EmergencyRepository repository, SystemConfigurationValues configuration, Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.configuration = configuration;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public CreationResult createOrRecover(AuthenticatedIdentity identity, EmergencyCreateInput input) {
        UUID userId = requireEnabledUser(identity, true);
        if (!repository.hasEligibleContact(userId)) {
            throw new RuleViolationException();
        }

        EmergencyData existing = repository.findOpenEmergency(userId, true).orElse(null);
        if (existing != null) {
            return new CreationResult(response(existing), false);
        }

        String message = effectiveMessage(userId, input.message());
        Instant now = clock.instant();
        UUID emergencyId = UUID.randomUUID();
        if (!repository.insertActiveEmergency(emergencyId, userId, message, now)) {
            EmergencyData recovered = repository.findOpenEmergency(userId, true).orElseThrow(RuleViolationException::new);
            return new CreationResult(response(recovered), false);
        }
        repository.insertInitialLocation(emergencyId, input.latitude(), input.longitude(), input.accuracyMeters(),
                input.capturedAt(), now);
        repository.insertInitialHistory(emergencyId, userId, now);
        eventPublisher.publishEvent(new EmergencyStatusChangedEvent(emergencyId, "ACTIVE", now));
        eventPublisher.publishEvent(new EmergencyCreatedEvent(emergencyId, message, input.latitude(), input.longitude()));
        return new CreationResult(new EmergencyResponse(emergencyId, "ACTIVE", null, now, null, null), true);
    }

    @Override
    @Transactional(readOnly = true)
    public EmergencyResponse active(AuthenticatedIdentity identity) {
        UUID userId = requireEnabledUser(identity, false);
        return repository.findOpenEmergency(userId, false).map(this::response).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public EmergencyResponse ownEmergency(AuthenticatedIdentity identity, UUID emergencyId) {
        UUID userId = requireEnabledUser(identity, false);
        return repository.findOwnEmergency(emergencyId, userId).map(this::response).orElseThrow(ResourceNotFoundException::new);
    }

    @Override
    @Transactional
    public void heartbeat(AuthenticatedIdentity identity, UUID emergencyId, LocationInput input) {
        UUID userId = requireEnabledUser(identity, false);
        EmergencyData emergency = repository.lockOwnEmergency(emergencyId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if ("FINALIZED".equals(emergency.status())) {
            throw new StateConflictException();
        }
        Instant now = clock.instant();
        repository.insertLocation(emergencyId, input.latitude(), input.longitude(), input.accuracyMeters(), input.capturedAt(), now);
        if ("OFFLINE".equals(emergency.status())) {
            transition(emergency, emergency.previousOperationalStatus(), null, userId, now, true);
        } else {
            repository.updateHeartbeat(emergencyId, emergency.status(), null, now);
        }
    }

    @Override
    @Transactional
    public void recordLocation(AuthenticatedIdentity identity, UUID emergencyId, LocationInput input) {
        UUID userId = requireEnabledUser(identity, false);
        EmergencyData emergency = repository.lockOwnEmergency(emergencyId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (!isOperational(emergency.status())) {
            throw new StateConflictException();
        }
        repository.insertLocation(emergencyId, input.latitude(), input.longitude(), input.accuracyMeters(), input.capturedAt(),
                clock.instant());
    }

    @Override
    @Transactional
    public void finish(AuthenticatedIdentity identity, UUID emergencyId) {
        UUID userId = requireEnabledUser(identity, false);
        EmergencyData emergency = repository.lockOwnEmergency(emergencyId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if ("FINALIZED".equals(emergency.status())) {
            return;
        }
        if (!isOperational(emergency.status())) {
            throw new StateConflictException();
        }
        transition(emergency, "FINALIZED", null, userId, clock.instant(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EmergencyResponse> ownHistory(AuthenticatedIdentity identity, int page, int size) {
        UUID userId = requireEnabledUser(identity, false);
        return new PageResponse<>(repository.findOwnFinalizedEmergencies(userId, size, page * size).stream()
                .map(this::response).toList(), page, size, repository.countOwnFinalizedEmergencies(userId));
    }

    @Override
    @Transactional
    public void startAttention(AuthenticatedIdentity identity, UUID emergencyId) {
        if (!"ENTITY_ADMIN".equals(identity.role())
                || repository.findEnabledAdministrator(identity.userId()).isEmpty()) {
            throw new ForbiddenException();
        }
        EmergencyData emergency = repository.lockEmergency(emergencyId).orElseThrow(ResourceNotFoundException::new);
        if ("IN_PROGRESS".equals(emergency.status())) {
            return;
        }
        if (!"ACTIVE".equals(emergency.status())) {
            throw new StateConflictException();
        }
        transition(emergency, "IN_PROGRESS", null, identity.userId(), clock.instant(), false);
    }

    @Override
    @Transactional
    public void markOfflineIfTimedOut(UUID emergencyId) {
        EmergencyData emergency = repository.lockEmergency(emergencyId).orElse(null);
        if (emergency == null || !isOperational(emergency.status())) {
            return;
        }
        Instant now = clock.instant();
        Instant lastActivity = emergency.lastHeartbeatAt() == null ? emergency.startedAt() : emergency.lastHeartbeatAt();
        if (lastActivity.plusSeconds(configuration.offlineTimeoutSeconds()).isAfter(now)) {
            return;
        }
        transition(emergency, "OFFLINE", emergency.status(), null, now, false);
    }

    private UUID requireEnabledUser(AuthenticatedIdentity identity, boolean lock) {
        if (!"USER".equals(identity.role())) {
            throw new ForbiddenException();
        }
        return (lock ? repository.lockEnabledUser(identity.userId()) : repository.findEnabledUser(identity.userId()))
                .map(EmergencyRepository.UserData::id)
                .orElseThrow(ForbiddenException::new);
    }

    private String effectiveMessage(UUID userId, String requestedMessage) {
        if (requestedMessage != null) {
            String normalized = requestedMessage.trim();
            if (normalized.isBlank()) {
                throw new RuleViolationException();
            }
            return normalized;
        }
        return repository.findValidEmergencyMessage(userId).map(String::trim)
                .filter(message -> !message.isBlank()).orElse(configuration.defaultSosMessage());
    }

    private EmergencyResponse response(EmergencyData emergency) {
        return new EmergencyResponse(emergency.id(), emergency.status(), emergency.previousOperationalStatus(),
                emergency.startedAt(), emergency.lastHeartbeatAt(), emergency.finalizedAt());
    }

    /** All lifecycle writers arrive here after locking the root emergency row. */
    private void transition(EmergencyData emergency, String nextStatus, String previousOperationalStatus,
            UUID actorUserId, Instant now, boolean updateHeartbeat) {
        if (nextStatus.equals(emergency.status())) {
            return;
        }
        if (updateHeartbeat) {
            repository.updateHeartbeat(emergency.id(), nextStatus, previousOperationalStatus, now);
        } else {
            repository.updateStatus(emergency.id(), nextStatus, previousOperationalStatus,
                    "FINALIZED".equals(nextStatus) ? now : null, now);
        }
        repository.insertStatusHistory(emergency.id(), repository.nextHistorySequence(emergency.id()), emergency.status(),
                nextStatus, actorUserId, now);
        eventPublisher.publishEvent(new EmergencyStatusChangedEvent(emergency.id(), nextStatus, now));
    }

    private boolean isOperational(String status) {
        return "ACTIVE".equals(status) || "IN_PROGRESS".equals(status);
    }
}
