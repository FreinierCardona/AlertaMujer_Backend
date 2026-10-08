package com.alertamujer.backend.administration.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.administration.repository.AdministrationRepository;
import com.alertamujer.backend.administration.repository.AdministrationRepository.ManagedUserData;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.identity.repository.ProfileRepository.UserProfileData;
import com.alertamujer.backend.identity.model.AccountOrigin;
import com.alertamujer.backend.identity.service.ProfileService;
import com.alertamujer.backend.identity.service.RegistrationRequestService;
import com.alertamujer.backend.shared.audit.AuditRepository;
import com.alertamujer.backend.shared.audit.AuditService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdministrationServiceImplTest {
    private final Instant now = Instant.parse("2026-10-07T18:00:00Z");
    private final UUID administratorId = UUID.randomUUID();
    private AdministrationRepository repository;
    private EmergencyService emergencies;
    private ProfileService profiles;
    private AuditService audit;
    private AdministrationServiceImpl service;
    private AuthenticatedIdentity administrator;

    @BeforeEach
    void setUp() {
        repository = mock(AdministrationRepository.class);
        emergencies = mock(EmergencyService.class);
        profiles = mock(ProfileService.class);
        audit = mock(AuditService.class);
        service = new AdministrationServiceImpl(repository, emergencies, profiles, mock(RegistrationRequestService.class),
                mock(AuditRepository.class), audit, Clock.fixed(now, ZoneOffset.UTC));
        administrator = new AuthenticatedIdentity(administratorId, UUID.randomUUID(), "ENTITY_ADMIN", false);
        when(repository.isEnabledAdministrator(administratorId)).thenReturn(true);
    }

    @Test
    void refusesAUserEvenWhenTheBodyCannotChooseAnotherRole() {
        AuthenticatedIdentity user = new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID(), "USER", false);
        assertThatThrownBy(() -> service.dashboard(user)).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).dashboard();
    }

    @Test
    void attentionDelegatesTheStateTransitionThenAppendsTheOnlyStatusAuditEvent() {
        UUID emergencyId = UUID.randomUUID();
        when(repository.findEmergencyOwner(emergencyId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(emergencies.startAttention(administrator, emergencyId)).thenReturn(true);
        service.startAttention(administrator, emergencyId);
        verify(emergencies).startAttention(administrator, emergencyId);
        verify(audit).record(any());
    }

    @Test
    void repeatedAttentionKeepsItsNoContentIdempotencyWithoutAnotherAuditEvent() {
        UUID emergencyId = UUID.randomUUID();
        when(repository.findEmergencyOwner(emergencyId)).thenReturn(Optional.of(UUID.randomUUID()));
        when(emergencies.startAttention(administrator, emergencyId)).thenReturn(false);

        service.startAttention(administrator, emergencyId);

        verify(emergencies).startAttention(administrator, emergencyId);
        verify(audit, never()).record(any());
    }

    @Test
    void onlyDisablesAnInactiveUserAndRevokesTheirSessions() {
        UUID userId = UUID.randomUUID();
        when(repository.lockManagedUser(userId)).thenReturn(Optional.of(new ManagedUserData(userId, "USER", "ENABLED",
                null, now.atZone(ZoneOffset.UTC).minusMonths(2).minusSeconds(1).toInstant(), null, now.minusSeconds(1))));
        when(repository.findUser(userId)).thenReturn(Optional.of(profile(userId, "DISABLED")));

        service.changeStatus(administrator, userId, "DISABLED");

        verify(repository).updateAccountStatus(userId, "DISABLED", now);
        verify(repository).revokeAllSessions(userId, now);
        verify(audit).record(any());
    }

    @Test
    void rejectsDisablingARecentlyActiveUser() {
        UUID userId = UUID.randomUUID();
        when(repository.lockManagedUser(userId)).thenReturn(Optional.of(new ManagedUserData(userId, "USER", "ENABLED",
                null, now.atZone(ZoneOffset.UTC).minusMonths(2).plusSeconds(1).toInstant(), null, now.minusSeconds(1))));
        assertThatThrownBy(() -> service.changeStatus(administrator, userId, "DISABLED"))
                .isInstanceOf(RuleViolationException.class);
        verify(repository, never()).updateAccountStatus(any(), any(), any());
    }

    @Test
    void neverAllowsAdministrativeStatusChangesForTheSingleAdministrator() {
        UUID userId = UUID.randomUUID();
        when(repository.lockManagedUser(userId)).thenReturn(Optional.of(new ManagedUserData(userId, "ENTITY_ADMIN", "ENABLED",
                null, null, null, now.minusSeconds(1))));
        assertThatThrownBy(() -> service.changeStatus(administrator, userId, "DISABLED")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delegatesEligibleDeletionToTheExistingSafeIdentityLifecycle() {
        UUID userId = UUID.randomUUID();
        service.deleteUser(administrator, userId);
        verify(profiles).deleteDisabledUserForAdministration(userId);
    }

    private UserProfileData profile(UUID userId, String status) {
        return new UserProfileData(userId, "@ana", "Ana", "Perez", "ana@example.com", "3000000000", "USER", status,
                AccountOrigin.SELF_REGISTERED, now.minusSeconds(1));
    }
}
