package com.alertamujer.backend.contacts.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.contacts.repository.ContactAlertRepository;
import com.alertamujer.backend.contacts.repository.ContactAlertRepository.AlertData;
import com.alertamujer.backend.contacts.repository.ContactAlertRepository.AlertDetailData;
import com.alertamujer.backend.contacts.repository.ContactAlertRepository.EvidenceData;
import com.alertamujer.backend.evidence.storage.EvidenceStorage;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContactAlertServiceImplTest {
    private final UUID contactId = UUID.randomUUID();
    private final UUID emergencyId = UUID.randomUUID();
    private final UUID evidenceId = UUID.randomUUID();
    private ContactAlertRepository repository;
    private ContactAlertServiceImpl service;
    private AuthenticatedIdentity contact;

    @BeforeEach
    void setUp() {
        repository = mock(ContactAlertRepository.class);
        service = new ContactAlertServiceImpl(repository, mock(EvidenceStorage.class));
        contact = new AuthenticatedIdentity(contactId, UUID.randomUUID(), "USER", false);
    }

    @Test
    void listsOnlyTheAuthorizedProjectionWithBoundedPaging() {
        Instant startedAt = Instant.parse("2026-10-15T12:00:00Z");
        when(repository.findAuthorizedAlerts(contactId, 20, 0)).thenReturn(List.of(
                new AlertData(emergencyId, "ACTIVE", "Ana Perez", "Privado", startedAt)));
        when(repository.countAuthorizedAlerts(contactId)).thenReturn(1L);

        var page = service.list(contact, 0, 20);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().message()).isEqualTo("Ana Perez está en peligro");
        assertThat(page.items().getFirst().title()).isEqualTo("Alerta de Emergencia");
        assertThat(page.items().getFirst().message()).doesNotContain("Privado");
        verify(repository).findAuthorizedAlerts(contactId, 20, 0);
    }

    @Test
    void exposesOnlyTheMinimalDetailAndMetadataAfterTheSameAuthorizationCheck() {
        Instant now = Instant.parse("2026-10-15T12:00:00Z");
        when(repository.findAuthorizedAlertDetail(contactId, emergencyId)).thenReturn(Optional.of(new AlertDetailData(
                emergencyId, "OFFLINE", "Ana Perez", now, "Necesito ayuda", new BigDecimal("4.609710"),
                new BigDecimal("-74.081750"), now)));
        when(repository.findAuthorizedEvidence(contactId, emergencyId)).thenReturn(List.of(new EvidenceData(evidenceId,
                "not-exposed.webp", "image/webp", 42, now)));

        var detail = service.detail(contact, emergencyId);

        assertThat(detail.userDisplayName()).isEqualTo("Ana Perez");
        assertThat(detail.lastConfirmedLocation().longitude()).isEqualByComparingTo("-74.081750");
        assertThat(detail.evidences()).extracting(e -> e.evidenceId()).containsExactly(evidenceId);
        assertThat(detail.toString()).doesNotContain("not-exposed.webp");
    }

    @Test
    void returnsNotFoundForAnUnauthorizedOrFinalizedIndividualAlert() {
        when(repository.findAuthorizedAlertDetail(contactId, emergencyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(contact, emergencyId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.evidences(contact, emergencyId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsNonUserActorsBeforeQueryingContactData() {
        AuthenticatedIdentity administrator = new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID(), "ENTITY_ADMIN", false);

        assertThatThrownBy(() -> service.list(administrator, 0, 20)).isInstanceOf(ForbiddenException.class);
    }
}
