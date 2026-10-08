package com.alertamujer.backend.emergency.service.impl;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.emergency.repository.EmergencyRepository;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmergencyTimeoutSchedulerTest {
    @Test
    void processesTheTimedOutBatchAndContinuesAfterOneFailure() {
        Instant now = Instant.parse("2026-10-07T18:00:00Z");
        EmergencyRepository repository = mock(EmergencyRepository.class);
        EmergencyService service = mock(EmergencyService.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.findTimedOutEmergencyIds(now.minusSeconds(180), 500)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException()).when(service).markOfflineIfTimedOut(first);
        var scheduler = new EmergencyTimeoutScheduler(repository, service, configuration(), Clock.fixed(now, ZoneOffset.UTC));

        scheduler.markTimedOutEmergenciesOffline();

        verify(repository).findTimedOutEmergencyIds(now.minusSeconds(180), 500);
        verify(service).markOfflineIfTimedOut(first);
        verify(service).markOfflineIfTimedOut(second);
    }

    private SystemConfigurationValues configuration() {
        return new SystemConfigurationValues("SOS", (short) 60, (short) 180, (short) 10, 1_048_576,
                (short) 500, (short) 180, (short) 5, (short) 3, (short) 300);
    }
}
