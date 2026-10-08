package com.alertamujer.backend.evidence.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.evidence.repository.EvidenceRepository;
import com.alertamujer.backend.evidence.storage.EvidenceStorage;
import com.alertamujer.backend.evidence.storage.StoredEvidenceFile;
import com.alertamujer.backend.shared.errors.RuleViolationException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.config.SystemConfigurationValues;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class EvidenceServiceImplTest {
    private EvidenceRepository repository;
    private EvidenceStorage storage;
    private EvidenceServiceImpl service;
    private UUID emergencyId;
    private AuthenticatedIdentity identity;

    @BeforeEach
    void setUp() {
        repository = mock(EvidenceRepository.class);
        storage = mock(EvidenceStorage.class);
        service = new EvidenceServiceImpl(repository, storage, configuration((short) 2),
                Clock.fixed(Instant.parse("2026-10-07T18:00:00Z"), ZoneOffset.UTC));
        emergencyId = UUID.randomUUID();
        identity = new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID(), "USER", false);
    }

    @Test
    void refusesOfflineEmergencyBeforeWritingTheFile() throws Exception {
        when(repository.lockOwnedEnabledEmergency(emergencyId, identity.userId()))
                .thenReturn(Optional.of(new EvidenceRepository.EmergencyData(emergencyId, "OFFLINE")));

        assertThatThrownBy(() -> service.upload(identity, emergencyId, file())).isInstanceOf(StateConflictException.class);
        verify(storage, never()).store(any());
    }

    @Test
    void refusesAnEmergencyOutsideTheOwnerRelationBeforeWritingTheFile() throws Exception {
        when(repository.lockOwnedEnabledEmergency(emergencyId, identity.userId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(identity, emergencyId, file())).isInstanceOf(ResourceNotFoundException.class);
        verify(storage, never()).store(any());
    }

    @Test
    void refusesEvidenceBeyondTheConfiguredCountBeforeWritingTheFile() throws Exception {
        when(repository.lockOwnedEnabledEmergency(emergencyId, identity.userId()))
                .thenReturn(Optional.of(new EvidenceRepository.EmergencyData(emergencyId, "ACTIVE")));
        when(repository.nextSequence(emergencyId)).thenReturn(3);

        assertThatThrownBy(() -> service.upload(identity, emergencyId, file())).isInstanceOf(RuleViolationException.class);
        verify(storage, never()).store(any());
    }

    @Test
    void compensatesThePrivateFileWhenMetadataPersistenceFails() throws Exception {
        when(repository.lockOwnedEnabledEmergency(emergencyId, identity.userId()))
                .thenReturn(Optional.of(new EvidenceRepository.EmergencyData(emergencyId, "ACTIVE")));
        when(repository.nextSequence(emergencyId)).thenReturn(1);
        when(storage.store(any())).thenReturn(new StoredEvidenceFile("00000000-0000-0000-0000-000000000001.webp", 42));
        doThrow(new RuntimeException("database unavailable")).when(repository).insert(any(), eq(emergencyId), eq(1),
                any(), any(), any(), eq(42), any());

        assertThatThrownBy(() -> service.upload(identity, emergencyId, file())).isInstanceOf(RuntimeException.class);
        verify(storage).deleteAfterPersistenceFailure("00000000-0000-0000-0000-000000000001.webp");
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "camera.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    private SystemConfigurationValues configuration(short maxEvidenceCount) {
        return new SystemConfigurationValues("Necesito ayuda", (short) 60, (short) 120, maxEvidenceCount,
                1_048_576, (short) 500, (short) 180, (short) 5, (short) 3, (short) 300);
    }
}
