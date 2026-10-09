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
import com.alertamujer.backend.emergency.dto.request.LocationInput;
import com.alertamujer.backend.emergency.repository.EmergencyRepository;
import com.alertamujer.backend.emergency.repository.EmergencyRepository.EmergencyData;
import com.alertamujer.backend.emergency.repository.EmergencyRepository.EmergencyDetailData;
import com.alertamujer.backend.emergency.repository.EmergencyRepository.UserData;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
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

    @Test
    void returnsOnlyTheOwnersAuthorizedDetailWithItsLastConfirmedLocation() {
        UUID emergencyId = UUID.randomUUID();
        EmergencyDetailData detail = new EmergencyDetailData(emergencyId, "ACTIVE", null, now.minusSeconds(30), now,
                null, "Snapshot", new BigDecimal("4.609710"), new BigDecimal("-74.081750"),
                new BigDecimal("8.5"), now.minusSeconds(2), now.minusSeconds(1));
        when(repository.findOwnEmergencyDetail(emergencyId, userId)).thenReturn(Optional.of(detail));

        var response = service.ownEmergency(identity, emergencyId);

        assertThat(response.messageSnapshot()).isEqualTo("Snapshot");
        assertThat(response.lastConfirmedLocation().latitude()).isEqualByComparingTo("4.609710");
        assertThat(response.lastConfirmedLocation().capturedAt()).isEqualTo(now.minusSeconds(2));
        verify(repository).findOwnEmergencyDetail(emergencyId, userId);
    }

    @Test
    void heartbeatRecoversOfflineAndAppendsTheNextHistoryEntryWhileHoldingTheOwnerEmergency() {
        UUID emergencyId = UUID.randomUUID();
        EmergencyData offline = new EmergencyData(emergencyId, "OFFLINE", "IN_PROGRESS", now.minusSeconds(300),
                now.minusSeconds(200), null);
        LocationInput location = location();
        when(repository.lockOwnEmergency(emergencyId, userId)).thenReturn(Optional.of(offline));
        when(repository.nextHistorySequence(emergencyId)).thenReturn(3);

        service.heartbeat(identity, emergencyId, location);

        verify(repository).insertLocation(emergencyId, location.latitude(), location.longitude(), location.accuracyMeters(),
                location.capturedAt(), now);
        verify(repository).updateHeartbeat(emergencyId, "IN_PROGRESS", null, now);
        verify(repository).insertStatusHistory(emergencyId, 3, "OFFLINE", "IN_PROGRESS", userId, now);
    }

    @Test
    void finalizedEmergencyRejectsNewLocationsAndNoLocationIsWritten() {
        UUID emergencyId = UUID.randomUUID();
        EmergencyData finalized = new EmergencyData(emergencyId, "FINALIZED", null, now.minusSeconds(300), now, now);
        when(repository.lockOwnEmergency(emergencyId, userId)).thenReturn(Optional.of(finalized));

        assertThatThrownBy(() -> service.recordLocation(identity, emergencyId, location()))
                .isInstanceOf(StateConflictException.class);

        verify(repository, never()).insertLocation(any(), any(), any(), any(), any(), any());
    }

    @Test
    void finishAppendsTerminalHistoryButARepeatedFinishIsIdempotent() {
        UUID emergencyId = UUID.randomUUID();
        EmergencyData active = new EmergencyData(emergencyId, "ACTIVE", null, now.minusSeconds(300), now, null);
        when(repository.lockOwnEmergency(emergencyId, userId)).thenReturn(Optional.of(active));
        when(repository.nextHistorySequence(emergencyId)).thenReturn(2);

        service.finish(identity, emergencyId);

        verify(repository).updateStatus(emergencyId, "FINALIZED", null, now, now);
        verify(repository).insertStatusHistory(emergencyId, 2, "ACTIVE", "FINALIZED", userId, now);

        EmergencyData finalized = new EmergencyData(emergencyId, "FINALIZED", null, now.minusSeconds(300), now, now);
        when(repository.lockOwnEmergency(emergencyId, userId)).thenReturn(Optional.of(finalized));
        service.finish(identity, emergencyId);
        verify(repository).insertStatusHistory(emergencyId, 2, "ACTIVE", "FINALIZED", userId, now);
    }

    @Test
    void timeoutPreservesOperationalStateAndDoesNotCreateALocation() {
        UUID emergencyId = UUID.randomUUID();
        EmergencyData active = new EmergencyData(emergencyId, "ACTIVE", null, now.minusSeconds(300),
                now.minusSeconds(181), null);
        when(repository.lockEmergency(emergencyId)).thenReturn(Optional.of(active));
        when(repository.nextHistorySequence(emergencyId)).thenReturn(2);

        service.markOfflineIfTimedOut(emergencyId);

        verify(repository).updateStatus(emergencyId, "OFFLINE", "ACTIVE", null, now);
        verify(repository).insertStatusHistory(emergencyId, 2, "ACTIVE", "OFFLINE", null, now);
        verify(repository, never()).insertLocation(any(), any(), any(), any(), any(), any());
    }

    @Test
    void enabledAdministratorCanOnlyStartAttentionFromActiveAndTheTransitionIsRecorded() {
        UUID emergencyId = UUID.randomUUID();
        UUID administratorId = UUID.randomUUID();
        AuthenticatedIdentity administrator = new AuthenticatedIdentity(administratorId, UUID.randomUUID(), "ENTITY_ADMIN", false);
        EmergencyData active = new EmergencyData(emergencyId, "ACTIVE", null, now.minusSeconds(300), now, null);
        when(repository.findEnabledAdministrator(administratorId)).thenReturn(Optional.of(new UserData(administratorId)));
        when(repository.lockEmergency(emergencyId)).thenReturn(Optional.of(active));
        when(repository.nextHistorySequence(emergencyId)).thenReturn(2);

        service.startAttention(administrator, emergencyId);

        verify(repository).updateStatus(emergencyId, "IN_PROGRESS", null, null, now);
        verify(repository).insertStatusHistory(emergencyId, 2, "ACTIVE", "IN_PROGRESS", administratorId, now);
    }

    @Test
    void ownHistoryUsesOnlyTheOwnerFinalizedPage() {
        UUID emergencyId = UUID.randomUUID();
        EmergencyData finalized = new EmergencyData(emergencyId, "FINALIZED", null, now.minusSeconds(300), now, now);
        when(repository.findOwnFinalizedEmergencies(userId, 20, 0)).thenReturn(List.of(finalized));
        when(repository.countOwnFinalizedEmergencies(userId)).thenReturn(1L);

        var page = service.ownHistory(identity, 0, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(response -> response.emergencyId()).containsExactly(emergencyId);
    }

    private EmergencyCreateInput input(String message) {
        return new EmergencyCreateInput(new BigDecimal("4.609710"), new BigDecimal("-74.081750"),
                new BigDecimal("8.5"), now.minusSeconds(2), message);
    }

    private LocationInput location() {
        return new LocationInput(new BigDecimal("4.609710"), new BigDecimal("-74.081750"),
                new BigDecimal("8.5"), now.minusSeconds(2));
    }

    private SystemConfigurationValues configuration() {
        return new SystemConfigurationValues("System SOS", (short) 30, (short) 180, (short) 10, 1_048_576,
                (short) 500, (short) 10, (short) 5, (short) 3, (short) 120);
    }
}
