package com.alertamujer.backend.contacts.service;

import com.alertamujer.backend.contacts.dto.response.ContactAlertDetailResponse;
import com.alertamujer.backend.contacts.dto.response.ContactAlertResponse;
import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageResponse;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/** Operaciones de solo lectura del panel de contactos definidas por HU-API-022. */
public interface ContactAlertService {
    PageResponse<ContactAlertResponse> list(AuthenticatedIdentity identity, int page, int size);
    ContactAlertDetailResponse detail(AuthenticatedIdentity identity, UUID emergencyId);
    List<EvidenceResponse> evidences(AuthenticatedIdentity identity, UUID emergencyId);
    EvidenceContent evidenceContent(AuthenticatedIdentity identity, UUID emergencyId, UUID evidenceId);

    record EvidenceContent(InputStream stream, long length) { }
}
