package com.alertamujer.backend.notification.service.impl;

import com.alertamujer.backend.notification.fcm.FcmResult;
import com.alertamujer.backend.notification.repository.NotificationRepository;
import com.alertamujer.backend.notification.repository.NotificationRepository.EligibleRecipient;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gives each database state transition its own committed transaction around the external FCM call. */
@Service
class NotificationAttemptService {
    private final NotificationRepository repository;
    private final Clock clock;

    @Autowired
    NotificationAttemptService(NotificationRepository repository) { this(repository, Clock.systemUTC()); }
    NotificationAttemptService(NotificationRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PendingAttempt prepare(UUID emergencyId, EligibleRecipient recipient) {
        Instant now = clock.instant();
        if (recipient.deviceTokenId() == null) {
            repository.insertAttempt(UUID.randomUUID(), emergencyId, recipient, "NO_TOKEN", now);
            return null;
        }
        UUID attemptId = UUID.randomUUID();
        return repository.insertAttempt(attemptId, emergencyId, recipient, "PENDING", now)
                ? new PendingAttempt(attemptId, recipient) : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(PendingAttempt attempt, FcmResult result) {
        Instant now = clock.instant();
        repository.completeAttempt(attempt.attemptId(), result.status().name(), result.providerMessageId(), result.errorCode(), now);
        if (result.status() == FcmResult.Status.INVALID_TOKEN) {
            repository.invalidateToken(attempt.recipient().deviceTokenId(), now);
        }
    }

    record PendingAttempt(UUID attemptId, EligibleRecipient recipient) { }
}
