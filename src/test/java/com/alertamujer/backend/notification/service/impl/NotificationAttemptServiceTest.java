package com.alertamujer.backend.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.notification.fcm.FcmResult;
import com.alertamujer.backend.notification.repository.NotificationRepository;
import com.alertamujer.backend.notification.repository.NotificationRepository.EligibleRecipient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationAttemptServiceTest {
    private final Instant now = Instant.parse("2026-10-07T18:00:00Z");

    @Test
    void createsNoTokenWithoutCallingTheProvider() {
        NotificationRepository repository = mock(NotificationRepository.class);
        EligibleRecipient recipient = new EligibleRecipient(UUID.randomUUID(), null, null);
        when(repository.insertAttempt(any(), any(), eq(recipient), eq("NO_TOKEN"), eq(now))).thenReturn(true);

        var prepared = service(repository).prepare(UUID.randomUUID(), recipient);

        assertThat(prepared).isNull();
        verify(repository).insertAttempt(any(), any(), eq(recipient), eq("NO_TOKEN"), eq(now));
    }

    @Test
    void marksProviderInvalidTokensInactiveAfterRecordingTheirKnownResult() {
        NotificationRepository repository = mock(NotificationRepository.class);
        UUID tokenId = UUID.randomUUID();
        NotificationAttemptService.PendingAttempt pending = new NotificationAttemptService.PendingAttempt(UUID.randomUUID(),
                new EligibleRecipient(UUID.randomUUID(), tokenId, "fcm-token"));

        service(repository).complete(pending, FcmResult.invalidToken("UNREGISTERED"));

        verify(repository).completeAttempt(pending.attemptId(), "INVALID_TOKEN", null, "UNREGISTERED", now);
        verify(repository).invalidateToken(tokenId, now);
    }

    private NotificationAttemptService service(NotificationRepository repository) {
        return new NotificationAttemptService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }
}
