package com.alertamujer.backend.emergency.service.impl;

import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.emergency.repository.EmergencyRepository;
import com.alertamujer.backend.emergency.repository.EmergencyRepository.EmergencyData;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the SOS root, its first location and its initial history event atomic. */
@Service
class EmergencyServiceImpl implements EmergencyService {

    private final EmergencyRepository repository;
    private final SystemConfigurationValues configuration;
    private final Clock clock;

    @Autowired
    EmergencyServiceImpl(EmergencyRepository repository, SystemConfigurationValues configuration) {
        this(repository, configuration, Clock.systemUTC());
    }

    EmergencyServiceImpl(EmergencyRepository repository, SystemConfigurationValues configuration, Clock clock) {
        this.repository = repository;
        this.configuration = configuration;
        this.clock = clock;
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
}
