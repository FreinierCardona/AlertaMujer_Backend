package com.alertamujer.backend.notification.service.impl;

import com.alertamujer.backend.notification.dto.request.DeviceTokenInput;
import com.alertamujer.backend.notification.repository.NotificationRepository;
import com.alertamujer.backend.notification.repository.NotificationRepository.TokenOwner;
import com.alertamujer.backend.notification.service.NotificationService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers only the authenticated owner's Android FCM token. */
@Service
class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository repository;
    private final Clock clock;

    @Autowired
    NotificationServiceImpl(NotificationRepository repository) { this(repository, Clock.systemUTC()); }
    NotificationServiceImpl(NotificationRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    @Override
    @Transactional
    public boolean registerDeviceToken(AuthenticatedIdentity identity, DeviceTokenInput input) {
        if (!"USER".equals(identity.role()) || !repository.isEnabledUser(identity.userId())) {
            throw new ForbiddenException();
        }
        String token = input.token().trim();
        TokenOwner existing = repository.findTokenOwner(token).orElse(null);
        if (existing != null) {
            refreshOwned(existing, identity.userId(), input, clock.instant());
            return false;
        }
        Instant now = clock.instant();
        try {
            repository.insertToken(UUID.randomUUID(), identity.userId(), token, input.platform().name(), now);
            return true;
        } catch (DataIntegrityViolationException exception) {
            TokenOwner winner = repository.findTokenOwner(token).orElseThrow(StateConflictException::new);
            refreshOwned(winner, identity.userId(), input, now);
            return false;
        }
    }

    private void refreshOwned(TokenOwner token, UUID userId, DeviceTokenInput input, Instant now) {
        if (!token.userId().equals(userId)) {
            throw new StateConflictException();
        }
        repository.refreshToken(token.tokenId(), input.platform().name(), now);
    }
}
