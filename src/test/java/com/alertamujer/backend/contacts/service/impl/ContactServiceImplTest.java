package com.alertamujer.backend.contacts.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.contacts.dto.request.ContactInvitationInput;
import com.alertamujer.backend.contacts.dto.response.ContactResponse;
import com.alertamujer.backend.contacts.repository.ContactRepository;
import com.alertamujer.backend.contacts.repository.ContactRepository.ContactData;
import com.alertamujer.backend.contacts.repository.ContactRepository.OwnContactData;
import com.alertamujer.backend.contacts.repository.ContactRepository.UserData;
import com.alertamujer.backend.contacts.service.ContactService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContactServiceImplTest {

    private final Instant now = Instant.parse("2026-10-07T18:00:00Z");
    private final UUID ownerId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private ContactRepository repository;
    private ContactServiceImpl service;
    private AuthenticatedIdentity owner;
    private AuthenticatedIdentity target;

    @BeforeEach
    void setUp() {
        repository = mock(ContactRepository.class);
        service = new ContactServiceImpl(repository, Clock.fixed(now, ZoneOffset.UTC));
        owner = new AuthenticatedIdentity(ownerId, UUID.randomUUID(), "USER", false);
        target = new AuthenticatedIdentity(targetId, UUID.randomUUID(), "USER", false);
        when(repository.findEnabledUser(ownerId)).thenReturn(Optional.of(new UserData(ownerId, "@ana")));
        when(repository.findEnabledUser(targetId)).thenReturn(Optional.of(new UserData(targetId, "@bea")));
    }

    @Test
    void createsOnlyAPendingInvitationWithTheFixedTwentyFourHourExpiry() {
        when(repository.findEnabledUserByUsername("@bea")).thenReturn(Optional.of(new UserData(targetId, "@bea")));
        when(repository.insertPending(any(), eq(ownerId), eq(targetId), eq(now.plusSeconds(86_400)), eq(now))).thenReturn(true);

        ContactService.InvitationResult result = service.invite(owner, new ContactInvitationInput("@bea"));

        assertThat(result.created()).isTrue();
        assertThat(result.invitation().status()).isEqualTo("PENDING");
        assertThat(result.invitation().expiresAt()).isEqualTo(now.plusSeconds(86_400));
    }

    @Test
    void returnsTheExistingPendingCanonicalPairForAnInverseConcurrentInvitation() {
        UUID contactId = UUID.randomUUID();
        ContactData pending = new ContactData(contactId, ownerId, targetId, "PENDING", now.plusSeconds(60), null);
        when(repository.findEnabledUserByUsername("@ana")).thenReturn(Optional.of(new UserData(ownerId, "@ana")));
        when(repository.insertPending(any(), eq(targetId), eq(ownerId), any(), eq(now))).thenReturn(false);
        when(repository.lockCanonicalPair(targetId, ownerId)).thenReturn(Optional.of(pending));

        ContactService.InvitationResult result = service.invite(target, new ContactInvitationInput("@ana"));

        assertThat(result.created()).isFalse();
        assertThat(result.invitation().contactId()).isEqualTo(contactId);
        verify(repository, never()).markExpired(any(), any());
    }

    @Test
    void rejectsSelfAndNonEligibleTargetsWithoutCreatingRows() {
        when(repository.findEnabledUserByUsername("@ana")).thenReturn(Optional.of(new UserData(ownerId, "@ana")));
        assertThatThrownBy(() -> service.invite(owner, new ContactInvitationInput("@ana")))
                .isInstanceOf(RuleViolationException.class);

        when(repository.findEnabledUserByUsername(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.invite(owner, new ContactInvitationInput("@missing")))
                .isInstanceOf(RuleViolationException.class);
        verify(repository, never()).insertPending(any(), any(), any(), any(), any());
    }

    @Test
    void onlyTheRecipientCanAcceptAndAcceptanceIsIdempotent() {
        UUID contactId = UUID.randomUUID();
        ContactData pending = new ContactData(contactId, ownerId, targetId, "PENDING", now.plusSeconds(60), null);
        when(repository.lockContact(contactId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.accept(owner, contactId)).isInstanceOf(ForbiddenException.class);
        service.accept(target, contactId);
        verify(repository).accept(contactId, now);

        when(repository.lockContact(contactId)).thenReturn(Optional.of(
                new ContactData(contactId, ownerId, targetId, "ACCEPTED", null, now)));
        service.accept(target, contactId);
        verify(repository).accept(contactId, now);
    }

    @Test
    void expiresThenReinvitesOnlyTheOriginalOwnerAndNeverReopensRejection() {
        UUID contactId = UUID.randomUUID();
        ContactData overdue = new ContactData(contactId, ownerId, targetId, "PENDING", now.minusSeconds(1), null);
        when(repository.lockContact(contactId)).thenReturn(Optional.of(overdue));

        var response = service.reinvite(owner, contactId);

        verify(repository).markExpired(contactId, now);
        verify(repository).reinvite(contactId, now.plusSeconds(86_400), now);
        assertThat(response.status()).isEqualTo("PENDING");

        when(repository.lockContact(contactId)).thenReturn(Optional.of(
                new ContactData(contactId, ownerId, targetId, "REJECTED", null, now)));
        assertThatThrownBy(() -> service.reinvite(owner, contactId)).isInstanceOf(StateConflictException.class);
    }

    @Test
    void ownListExpiresPendingRowsAndKeepsCurrentEligibility() {
        UUID contactId = UUID.randomUUID();
        when(repository.countOwnContacts(ownerId)).thenReturn(1L);
        when(repository.findOwnContacts(ownerId, 20, 0)).thenReturn(List.of(
                new OwnContactData(contactId, "ACCEPTED", null, true, "@bea", "Bea", "Rojas", false)));

        var page = service.ownContacts(owner, 0, 20);

        verify(repository).expirePendingForUser(ownerId, now);
        assertThat(page.items()).containsExactly(new ContactResponse(contactId, "ACCEPTED", null, false,
                new ContactResponse.Counterpart("@bea", "Bea", "Rojas"), ContactResponse.Direction.SENT, List.of()));
    }

    @Test
    void exposesOnlyTheBackendAuthorizedActionsForTheCurrentParticipant() {
        UUID receivedId = UUID.randomUUID();
        UUID expiredId = UUID.randomUUID();
        when(repository.countOwnContacts(ownerId)).thenReturn(2L);
        when(repository.findOwnContacts(ownerId, 20, 0)).thenReturn(List.of(
                new OwnContactData(receivedId, "PENDING", now.plusSeconds(60), false, "@bea", "Bea", "Rojas", true),
                new OwnContactData(expiredId, "EXPIRED", null, true, "@carla", "Carla", "Mora", true)));

        var page = service.ownContacts(owner, 0, 20);

        assertThat(page.items().get(0).direction()).isEqualTo(ContactResponse.Direction.RECEIVED);
        assertThat(page.items().get(0).allowedActions())
                .containsExactly(ContactResponse.Action.ACCEPT, ContactResponse.Action.REJECT);
        assertThat(page.items().get(1).direction()).isEqualTo(ContactResponse.Direction.SENT);
        assertThat(page.items().get(1).allowedActions()).containsExactly(ContactResponse.Action.REINVITE);
    }
}
