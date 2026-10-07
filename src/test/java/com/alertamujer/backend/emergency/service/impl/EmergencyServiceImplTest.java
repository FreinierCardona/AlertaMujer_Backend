package com.alertamujer.backend.emergency.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.repository.EmergencyRepository;
import com.alertamujer.backend.emergency.repository.EmergencyRepository.EmergencyData;
import com.alertamujer.backend.emergency.repository.EmergencyRepository.UserData;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmergencyServiceImplTest {

    private final Instant now = Instant.parse("2026-10-07T18:00:00Z");
    private final UUID userId = UUID.randomUUID();
    private EmergencyRepository repository;
    private EmergencyServiceImpl service;
    private AuthenticatedIdentity identity;

    @BeforeEach
    void setUp() {
        repository = mock(EmergencyRepository.class);
        service = new EmergencyServiceImpl(repository, configuration(), Clock.fixed(now, ZoneOffset.UTC));
        identity = new AuthenticatedIdentity(userId, UUID.randomUUID(), "USER", false);
        when(repository.lockEnabledUser(userId)).thenReturn(Optional.of(new UserData(userId)));
        when(repository.findEnabledUser(userId)).thenReturn(Optional.of(new UserData(userId)));
    }

    @Test
    void createsRootInitialLocationAndInitialHistoryUsingTheSystemDefault() {
        when(repository.hasEligibleContact(userId)).thenReturn(true);
        when(repository.findOpenEmergency(userId, true)).thenReturn(Optional.empty());
        when(repository.findValidEmergencyMessage(userId)).thenReturn(Optional.empty());
        when(repository.insertActiveEmergency(any(), eq(userId), eq("System SOS"), eq(now))).thenReturn(true);
        EmergencyCreateInput input = input(null);

        var result = service.createOrRecover(identity, input);

        assertThat(result.created()).isTrue();
        assertThat(result.emergency().status()).isEqualTo("ACTIVE");
        assertThat(result.emergency().startedAt()).isEqualTo(now);
        verify(repository).insertInitialLocation(result.emergency().emergencyId(), input.latitude(), input.longitude(),
                input.accuracyMeters(), input.capturedAt(), now);
        verify(repository).insertInitialHistory(result.emergency().emergencyId(), userId, now);
    }

    @Test
    void returnsTheExistingOpenEmergencyWithoutDuplicatingChildRows() {
        UUID emergencyId = UUID.randomUUID();
        EmergencyData existing = new EmergencyData(emergencyId, "ACTIVE", null, now.minusSeconds(15), null, null);
        when(repository.hasEligibleContact(userId)).thenReturn(true);
        when(repository.findOpenEmergency(userId, true)).thenReturn(Optional.of(existing));

        var result = service.createOrRecover(identity, input("Will not replace snapshot"));

        assertThat(result.created()).isFalse();
        assertThat(result.emergency().emergencyId()).isEqualTo(emergencyId);
        verify(repository, never()).insertActiveEmergency(any(), any(), any(), any());
        verify(repository, never()).insertInitialLocation(any(), any(), any(), any(), any(), any());
        verify(repository, never()).insertInitialHistory(any(), any(), any());
    }

    @Test
    void rejectsMissingEligibleContactBeforeWritingAnyPartOfTheSos() {
        when(repository.hasEligibleContact(userId)).thenReturn(false);

        assertThatThrownBy(() -> service.createOrRecover(identity, input(null))).isInstanceOf(RuleViolationException.class);

        verify(repository, never()).findOpenEmergency(any(), any(Boolean.class));
        verify(repository, never()).insertActiveEmergency(any(), any(), any(), any());
    }

    @Test
    void rejectsABlankRequestedMessageInsteadOfPersistingAnInvalidSnapshot() {
        when(repository.hasEligibleContact(userId)).thenReturn(true);
        when(repository.findOpenEmergency(userId, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrRecover(identity, input("  "))).isInstanceOf(RuleViolationException.class);

        verify(repository, never()).insertActiveEmergency(any(), any(), any(), any());
    }

    @Test
    void readsOnlyTheOwnersOpenEmergency() {
        UUID emergencyId = UUID.randomUUID();
        EmergencyData existing = new EmergencyData(emergencyId, "ACTIVE", null, now, null, null);
        when(repository.findOpenEmergency(userId, false)).thenReturn(Optional.of(existing));

        assertThat(service.active(identity).emergencyId()).isEqualTo(emergencyId);
    }

    private EmergencyCreateInput input(String message) {
        return new EmergencyCreateInput(new BigDecimal("4.609710"), new BigDecimal("-74.081750"),
                new BigDecimal("8.5"), now.minusSeconds(2), message);
    }

    private SystemConfigurationValues configuration() {
        return new SystemConfigurationValues("System SOS", (short) 30, (short) 180, (short) 10, 1_048_576,
                (short) 500, (short) 10, (short) 5, (short) 3, (short) 120);
    }
}
