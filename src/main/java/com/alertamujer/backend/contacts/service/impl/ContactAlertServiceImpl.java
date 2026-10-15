package com.alertamujer.backend.contacts.service.impl;

import com.alertamujer.backend.contacts.dto.response.ContactAlertDetailResponse;
import com.alertamujer.backend.contacts.dto.response.ContactAlertResponse;
import com.alertamujer.backend.contacts.repository.ContactAlertRepository;
import com.alertamujer.backend.contacts.repository.ContactAlertRepository.AlertDetailData;
import com.alertamujer.backend.contacts.repository.ContactAlertRepository.EvidenceData;
import com.alertamujer.backend.contacts.service.ContactAlertService;
import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import com.alertamujer.backend.evidence.storage.EvidenceStorage;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ContactAlertServiceImpl implements ContactAlertService {
    private final ContactAlertRepository repository;
    private final EvidenceStorage storage;

    ContactAlertServiceImpl(ContactAlertRepository repository, EvidenceStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ContactAlertResponse> list(AuthenticatedIdentity identity, int page, int size) {
        UUID contactUserId = requireEnabledUserRole(identity);
        return new PageResponse<>(repository.findAuthorizedAlerts(contactUserId, size, (long) page * size).stream()
                .map(alert -> new ContactAlertResponse(alert.id(), "Alerta de Emergencia", alert.status(),
                        alert.userDisplayName(), alert.userDisplayName() + " está en peligro", alert.startedAt()))
                .toList(), page, size, repository.countAuthorizedAlerts(contactUserId));
    }

    @Override
    @Transactional(readOnly = true)
    public ContactAlertDetailResponse detail(AuthenticatedIdentity identity, UUID emergencyId) {
        UUID contactUserId = requireEnabledUserRole(identity);
        AlertDetailData alert = repository.findAuthorizedAlertDetail(contactUserId, emergencyId)
                .orElseThrow(ResourceNotFoundException::new);
        return new ContactAlertDetailResponse(alert.id(), alert.status(), alert.userDisplayName(), alert.startedAt(),
                alert.messageSnapshot(), alert.latitude() == null ? null : new ContactAlertDetailResponse.LastConfirmedLocation(
                        alert.latitude(), alert.longitude(), alert.capturedAt()), evidence(contactUserId, emergencyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> evidences(AuthenticatedIdentity identity, UUID emergencyId) {
        UUID contactUserId = requireEnabledUserRole(identity);
        // An empty evidence list must still prove that the alert is authorized.
        repository.findAuthorizedAlertDetail(contactUserId, emergencyId).orElseThrow(ResourceNotFoundException::new);
        return evidence(contactUserId, emergencyId);
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceContent evidenceContent(AuthenticatedIdentity identity, UUID emergencyId, UUID evidenceId) {
        UUID contactUserId = requireEnabledUserRole(identity);
        EvidenceData evidence = repository.findAuthorizedEvidence(contactUserId, emergencyId, evidenceId)
                .orElseThrow(ResourceNotFoundException::new);
        try {
            InputStream stream = storage.open(evidence.reference());
            return new EvidenceContent(stream, evidence.sizeBytes());
        } catch (NoSuchFileException exception) {
            throw new ResourceNotFoundException();
        } catch (IOException exception) {
            throw new ResourceNotFoundException();
        }
    }

    private List<EvidenceResponse> evidence(UUID contactUserId, UUID emergencyId) {
        return repository.findAuthorizedEvidence(contactUserId, emergencyId).stream()
                .map(data -> new EvidenceResponse(data.id(), data.mimeType(), data.sizeBytes(), data.receivedAt())).toList();
    }

    private UUID requireEnabledUserRole(AuthenticatedIdentity identity) {
        if (!"USER".equals(identity.role())) throw new ForbiddenException();
        return identity.userId();
    }
}
