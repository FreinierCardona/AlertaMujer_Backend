package com.alertamujer.backend.notification.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.emergency.event.EmergencyCreatedEvent;
import com.alertamujer.backend.notification.fcm.FcmClient;
import com.alertamujer.backend.notification.fcm.FcmResult;
import com.alertamujer.backend.notification.fcm.FcmUnavailableBeforeSendException;
import com.alertamujer.backend.notification.repository.NotificationRepository;
import com.alertamujer.backend.notification.repository.NotificationRepository.EligibleRecipient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDispatchServiceTest {
    @Test
    void sendsOnlyTheSelectedActiveTokenAndCompletesTheKnownResult() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationAttemptService attempts = mock(NotificationAttemptService.class);
        FcmClient fcm = mock(FcmClient.class);
        UUID emergencyId = UUID.randomUUID();
        EligibleRecipient recipient = new EligibleRecipient(UUID.randomUUID(), UUID.randomUUID(), "fcm-token");
        NotificationAttemptService.PendingAttempt pending = new NotificationAttemptService.PendingAttempt(UUID.randomUUID(), recipient);
        when(repository.findEligibleRecipients(emergencyId)).thenReturn(List.of(recipient));
        when(attempts.prepare(emergencyId, recipient)).thenReturn(pending);
        when(fcm.send(any())).thenReturn(FcmResult.sent("message-id"));

        new NotificationDispatchService(repository, attempts, fcm).dispatch(event(emergencyId));

        verify(fcm).send(any());
        verify(attempts).complete(pending, FcmResult.sent("message-id"));
    }

    @Test
    void keepsPendingWhenTheProviderCannotBeReachedBeforeSending() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationAttemptService attempts = mock(NotificationAttemptService.class);
        FcmClient fcm = mock(FcmClient.class);
        UUID emergencyId = UUID.randomUUID();
        EligibleRecipient recipient = new EligibleRecipient(UUID.randomUUID(), UUID.randomUUID(), "fcm-token");
        NotificationAttemptService.PendingAttempt pending = new NotificationAttemptService.PendingAttempt(UUID.randomUUID(), recipient);
        when(repository.findEligibleRecipients(emergencyId)).thenReturn(List.of(recipient));
        when(attempts.prepare(emergencyId, recipient)).thenReturn(pending);
        when(fcm.send(any())).thenThrow(new FcmUnavailableBeforeSendException());

        new NotificationDispatchService(repository, attempts, fcm).dispatch(event(emergencyId));

        verify(attempts, never()).complete(any(), any());
    }

    @Test
    void recordsTheKnownTimeoutWithoutStoppingTheSosNotificationFlow() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationAttemptService attempts = mock(NotificationAttemptService.class);
        FcmClient fcm = mock(FcmClient.class);
        UUID emergencyId = UUID.randomUUID();
        EligibleRecipient recipient = new EligibleRecipient(UUID.randomUUID(), UUID.randomUUID(), "fcm-token");
        NotificationAttemptService.PendingAttempt pending = new NotificationAttemptService.PendingAttempt(UUID.randomUUID(), recipient);
        when(repository.findEligibleRecipients(emergencyId)).thenReturn(List.of(recipient));
        when(attempts.prepare(emergencyId, recipient)).thenReturn(pending);
        when(fcm.send(any())).thenReturn(FcmResult.failed("TIMEOUT"));

        new NotificationDispatchService(repository, attempts, fcm).dispatch(event(emergencyId));

        verify(attempts).complete(pending, FcmResult.failed("TIMEOUT"));
    }

    @Test
    void doesNotCallFcmForAnEligibleContactWithoutAnActiveToken() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationAttemptService attempts = mock(NotificationAttemptService.class);
        FcmClient fcm = mock(FcmClient.class);
        UUID emergencyId = UUID.randomUUID();
        EligibleRecipient recipient = new EligibleRecipient(UUID.randomUUID(), null, null);
        when(repository.findEligibleRecipients(emergencyId)).thenReturn(List.of(recipient));
        when(attempts.prepare(emergencyId, recipient)).thenReturn(null);

        new NotificationDispatchService(repository, attempts, fcm).dispatch(event(emergencyId));

        verify(fcm, never()).send(any());
    }

    private static EmergencyCreatedEvent event(UUID emergencyId) {
        return new EmergencyCreatedEvent(emergencyId, "Necesito ayuda", new BigDecimal("4.60"), new BigDecimal("-74.08"));
    }
}
