package com.alertamujer.backend.notification.service.impl;

import com.alertamujer.backend.emergency.event.EmergencyCreatedEvent;
import com.alertamujer.backend.notification.fcm.FcmClient;
import com.alertamujer.backend.notification.fcm.FcmNotification;
import com.alertamujer.backend.notification.fcm.FcmResult;
import com.alertamujer.backend.notification.fcm.FcmUnavailableBeforeSendException;
import com.alertamujer.backend.notification.repository.NotificationRepository;
import com.alertamujer.backend.notification.repository.NotificationRepository.EligibleRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Selects contacts and dispatches one independent FCM attempt for each of them. */
@Service
class NotificationDispatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDispatchService.class);
    private final NotificationRepository repository;
    private final NotificationAttemptService attempts;
    private final FcmClient fcmClient;

    NotificationDispatchService(NotificationRepository repository, NotificationAttemptService attempts, FcmClient fcmClient) {
        this.repository = repository; this.attempts = attempts; this.fcmClient = fcmClient;
    }

    public void dispatch(EmergencyCreatedEvent event) {
        for (EligibleRecipient recipient : repository.findEligibleRecipients(event.emergencyId())) {
            try {
                NotificationAttemptService.PendingAttempt attempt = attempts.prepare(event.emergencyId(), recipient);
                if (attempt == null) continue;
                FcmResult result;
                try {
                    result = fcmClient.send(new FcmNotification(event.emergencyId(), recipient.fcmToken(), event.message(),
                            event.latitude(), event.longitude()));
                } catch (FcmUnavailableBeforeSendException exception) {
                    LOGGER.warn("FCM send could not start. emergencyId={}", event.emergencyId());
                    continue;
                } catch (RuntimeException exception) {
                    result = FcmResult.failed("FCM_ERROR");
                }
                attempts.complete(attempt, result);
                LOGGER.info("FCM attempt completed. emergencyId={} result={}", event.emergencyId(), result.status());
            } catch (RuntimeException exception) {
                LOGGER.warn("FCM recipient processing failed. emergencyId={}", event.emergencyId());
            }
        }
    }
}
