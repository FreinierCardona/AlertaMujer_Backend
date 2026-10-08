package com.alertamujer.backend.evidence.service.impl;

import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import com.alertamujer.backend.evidence.repository.EvidenceRepository;
import com.alertamujer.backend.evidence.repository.EvidenceRepository.EvidenceData;
import com.alertamujer.backend.evidence.storage.EvidenceStorage;
import com.alertamujer.backend.evidence.storage.StoredEvidenceFile;
import com.alertamujer.backend.evidence.service.EvidenceService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Coordinates one local file and one metadata row, compensating the file when insertion fails. */
@Service
class EvidenceServiceImpl implements EvidenceService {
    private final EvidenceRepository repository;
    private final EvidenceStorage storage;
    private final SystemConfigurationValues configuration;
    private final Clock clock;

    @Autowired
    EvidenceServiceImpl(EvidenceRepository repository, EvidenceStorage storage, SystemConfigurationValues configuration) {
        this(repository, storage, configuration, Clock.systemUTC());
    }

    EvidenceServiceImpl(EvidenceRepository repository, EvidenceStorage storage, SystemConfigurationValues configuration, Clock clock) {
        this.repository = repository; this.storage = storage; this.configuration = configuration; this.clock = clock;
    }

    @Override
    @Transactional
    public EvidenceResponse upload(AuthenticatedIdentity identity, UUID emergencyId, MultipartFile file) {
        requireUser(identity);
        var emergency = repository.lockOwnedEnabledEmergency(emergencyId, identity.userId())
                .orElseThrow(ResourceNotFoundException::new);
        if (!isOperational(emergency.status())) throw new StateConflictException();
        int sequence = repository.nextSequence(emergencyId);
        if (sequence > configuration.maxEvidenceCount()) throw new RuleViolationException();

        StoredEvidenceFile stored = null;
        try {
            stored = storage.store(file);
            Instant receivedAt = clock.instant();
            UUID evidenceId = UUID.randomUUID();
            repository.insert(evidenceId, emergencyId, sequence, stored.reference(), safeOriginalName(file),
                    file.getContentType(), stored.sizeBytes(), receivedAt);
            return new EvidenceResponse(evidenceId, "image/webp", stored.sizeBytes(), receivedAt);
        } catch (IOException exception) {
            throw new RuleViolationException();
        } catch (RuntimeException exception) {
            if (stored != null) storage.deleteAfterPersistenceFailure(stored.reference());
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceResponse> list(AuthenticatedIdentity identity, UUID emergencyId) {
        requireReader(identity);
        if (!repository.hasAuthorizedEmergency(emergencyId, identity.userId(), identity.role())) {
            throw new ResourceNotFoundException();
        }
        return repository.findAuthorizedEmergencyEvidence(emergencyId, identity.userId(), identity.role()).stream()
                .map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceContent content(AuthenticatedIdentity identity, UUID evidenceId) {
        requireReader(identity);
        EvidenceData data = repository.findAuthorizedEvidence(evidenceId, identity.userId(), identity.role())
                .orElseThrow(ResourceNotFoundException::new);
        try {
            return new EvidenceContent(storage.open(data.reference()), data.sizeBytes());
        } catch (NoSuchFileException exception) {
            throw new ResourceNotFoundException();
        } catch (IOException exception) {
            throw new ResourceNotFoundException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int reconcileOrphans() {
        return storage.deleteUnreferencedOlderThan(repository.findAllReferences(), clock.instant().minusSeconds(24 * 60 * 60));
    }

    private void requireUser(AuthenticatedIdentity identity) {
        if (!"USER".equals(identity.role())) throw new ForbiddenException();
    }

    private void requireReader(AuthenticatedIdentity identity) {
        if (!"USER".equals(identity.role()) && !"ENTITY_ADMIN".equals(identity.role())) throw new ForbiddenException();
    }

    private EvidenceResponse response(EvidenceData data) {
        return new EvidenceResponse(data.id(), data.mimeType(), data.sizeBytes(), data.receivedAt());
    }

    private boolean isOperational(String status) {
        return "ACTIVE".equals(status) || "IN_PROGRESS".equals(status);
    }

    private String safeOriginalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) return null;
        String normalized = name.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return normalized.isBlank() ? null : normalized.substring(0, Math.min(normalized.length(), 255));
    }
}
