package com.alertamujer.backend.administration.service.impl;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.administration.repository.AdministrationRepository;
import com.alertamujer.backend.administration.service.AdministrationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InactiveAccountSchedulerTest {
    @Test
    void usesTheUtcCalendarCutoffAndContinuesWithTheNextAccount() {
        Instant now = Instant.parse("2026-10-07T18:00:00Z");
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(3).toInstant();
        AdministrationRepository repository = mock(AdministrationRepository.class);
        AdministrationService service = mock(AdministrationService.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.findInactiveEnabledUserIds(cutoff, 500)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException()).when(service).disableUserIfStillInactive(first);
        var scheduler = new InactiveAccountScheduler(repository, service, Clock.fixed(now, ZoneOffset.UTC));

        scheduler.disableInactiveAccounts();

        verify(repository).findInactiveEnabledUserIds(cutoff, 500);
        verify(service).disableUserIfStillInactive(first);
        verify(service).disableUserIfStillInactive(second);
    }
}
