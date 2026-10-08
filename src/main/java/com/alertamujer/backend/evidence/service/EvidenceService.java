package com.alertamujer.backend.evidence.service;

import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Application operations for photographic evidence only. */
public interface EvidenceService {
    EvidenceResponse upload(AuthenticatedIdentity identity, UUID emergencyId, MultipartFile file);
    List<EvidenceResponse> list(AuthenticatedIdentity identity, UUID emergencyId);
    EvidenceContent content(AuthenticatedIdentity identity, UUID evidenceId);

    /** Called by the future HU-API-018 hourly scheduler; it is intentionally not an HTTP operation. */
    int reconcileOrphans();

    record EvidenceContent(InputStream stream, long length) { }
}
