package com.alertamujer.backend.contacts.service.impl;

import com.alertamujer.backend.contacts.dto.request.ContactInvitationInput;
import com.alertamujer.backend.contacts.dto.response.ContactInvitationResponse;
import com.alertamujer.backend.contacts.dto.response.ContactResponse;
import com.alertamujer.backend.contacts.dto.response.DirectoryUserResponse;
import com.alertamujer.backend.contacts.dto.response.PageResponse;
import com.alertamujer.backend.contacts.repository.ContactRepository;
import com.alertamujer.backend.contacts.repository.ContactRepository.ContactData;
import com.alertamujer.backend.contacts.repository.ContactRepository.UserData;
import com.alertamujer.backend.contacts.service.ContactService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies the contact invitation state machine; PostgreSQL enforces the canonical pair. */
@Service
class ContactServiceImpl implements ContactService {

    private static final long INVITATION_SECONDS = 24L * 60 * 60;
    private final ContactRepository repository;
    private final Clock clock;

    @Autowired
    ContactServiceImpl(ContactRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ContactServiceImpl(ContactRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DirectoryUserResponse> directory(AuthenticatedIdentity identity, String query, int page, int size) {
        UserData actor = requireEnabledUser(identity);
        long total = repository.countDirectory(actor.id(), query);
        return new PageResponse<>(repository.findDirectory(actor.id(), query, size, offset(page, size)).stream()
                .map(user -> new DirectoryUserResponse(user.username(), user.firstNames(), user.lastNames())).toList(), page, size, total);
    }

    @Override
    @Transactional
    public InvitationResult invite(AuthenticatedIdentity identity, ContactInvitationInput input) {
        UserData actor = requireEnabledUser(identity);
        UserData target = repository.findEnabledUserByUsername(input.username().trim()).orElseThrow(RuleViolationException::new);
        if (actor.id().equals(target.id())) {
            throw new RuleViolationException();
        }
        Instant now = clock.instant();
        UUID contactId = UUID.randomUUID();
        if (repository.insertPending(contactId, actor.id(), target.id(), now.plusSeconds(INVITATION_SECONDS), now)) {
            return new InvitationResult(response(new ContactData(contactId, actor.id(), target.id(), "PENDING",
                    now.plusSeconds(INVITATION_SECONDS), null)), true);
        }
        ContactData existing = repository.lockCanonicalPair(actor.id(), target.id()).orElseThrow(RuleViolationException::new);
        existing = expireIfNeeded(existing, now);
        if ("PENDING".equals(existing.status())) {
            return new InvitationResult(response(existing), false);
        }
        throw new RuleViolationException();
    }

    @Override
    @Transactional
    public void accept(AuthenticatedIdentity identity, UUID contactId) {
        UserData actor = requireEnabledUser(identity);
        ContactData relation = recipientRelation(actor, contactId);
        Instant now = clock.instant();
        relation = expireIfNeeded(relation, now);
        if ("ACCEPTED".equals(relation.status())) {
            return;
        }
        if (!"PENDING".equals(relation.status())) {
            throw new StateConflictException();
        }
        repository.accept(relation.id(), now);
    }

    @Override
    @Transactional
    public void reject(AuthenticatedIdentity identity, UUID contactId) {
        UserData actor = requireEnabledUser(identity);
        ContactData relation = recipientRelation(actor, contactId);
        Instant now = clock.instant();
        relation = expireIfNeeded(relation, now);
        if ("REJECTED".equals(relation.status())) {
            return;
        }
        if (!"PENDING".equals(relation.status())) {
            throw new StateConflictException();
        }
        repository.reject(relation.id(), now);
    }

    @Override
    @Transactional
    public ContactInvitationResponse reinvite(AuthenticatedIdentity identity, UUID contactId) {
        UserData actor = requireEnabledUser(identity);
        ContactData relation = repository.lockContact(contactId).orElseThrow(ResourceNotFoundException::new);
        if (!relation.ownerId().equals(actor.id())) {
            throw new ForbiddenException();
        }
        Instant now = clock.instant();
        relation = expireIfNeeded(relation, now);
        if ("PENDING".equals(relation.status())) {
            return response(relation);
        }
        if (!"EXPIRED".equals(relation.status())) {
            throw new StateConflictException();
        }
        Instant expiresAt = now.plusSeconds(INVITATION_SECONDS);
        repository.reinvite(relation.id(), expiresAt, now);
        return new ContactInvitationResponse(relation.id(), "PENDING", expiresAt);
    }

    @Override
    @Transactional
    public PageResponse<ContactResponse> ownContacts(AuthenticatedIdentity identity, int page, int size) {
        UserData actor = requireEnabledUser(identity);
        repository.expirePendingForUser(actor.id(), clock.instant());
        long total = repository.countOwnContacts(actor.id());
        return new PageResponse<>(repository.findOwnContacts(actor.id(), size, offset(page, size)).stream()
                .map(contact -> new ContactResponse(contact.id(), contact.status(), contact.expiresAt(), contact.eligible())).toList(),
                page, size, total);
    }

    private UserData requireEnabledUser(AuthenticatedIdentity identity) {
        if (!"USER".equals(identity.role())) {
            throw new ForbiddenException();
        }
        return repository.findEnabledUser(identity.userId()).orElseThrow(ForbiddenException::new);
    }

    private ContactData recipientRelation(UserData actor, UUID contactId) {
        ContactData relation = repository.lockContact(contactId).orElseThrow(ResourceNotFoundException::new);
        if (!relation.targetId().equals(actor.id())) {
            throw new ForbiddenException();
        }
        return relation;
    }

    private ContactData expireIfNeeded(ContactData relation, Instant now) {
        if ("PENDING".equals(relation.status()) && !relation.expiresAt().isAfter(now)) {
            repository.markExpired(relation.id(), now);
            return new ContactData(relation.id(), relation.ownerId(), relation.targetId(), "EXPIRED", null, now);
        }
        return relation;
    }

    private ContactInvitationResponse response(ContactData relation) {
        return new ContactInvitationResponse(relation.id(), relation.status(), relation.expiresAt());
    }

    private long offset(int page, int size) {
        return Math.multiplyExact((long) page, size);
    }
}
