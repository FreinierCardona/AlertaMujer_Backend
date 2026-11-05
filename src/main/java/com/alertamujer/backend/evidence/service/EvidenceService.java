package com.alertamujer.backend.evidence.service;

import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Operaciones de aplicación exclusivamente para evidencias fotográficas. */
public interface EvidenceService {
    EvidenceResponse upload(AuthenticatedIdentity identity, UUID emergencyId, MultipartFile file);
    List<EvidenceResponse> list(AuthenticatedIdentity identity, UUID emergencyId);
    EvidenceContent content(AuthenticatedIdentity identity, UUID evidenceId);

    /** Invocado por el futuro planificador horario de HU-API-018; no es una operación HTTP. */
    int reconcileOrphans();

    record EvidenceContent(InputStream stream, long length) { }
}
