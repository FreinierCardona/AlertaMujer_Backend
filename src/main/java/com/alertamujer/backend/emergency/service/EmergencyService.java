package com.alertamujer.backend.emergency.service;

import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.dto.request.LocationInput;
import com.alertamujer.backend.emergency.dto.response.EmergencyDetailResponse;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageResponse;
import java.util.UUID;

/** SOS creation/recovery and reads established by HU-API-012. */
public interface EmergencyService {
    CreationResult createOrRecover(AuthenticatedIdentity identity, EmergencyCreateInput input);
    EmergencyResponse active(AuthenticatedIdentity identity);
    EmergencyDetailResponse ownEmergency(AuthenticatedIdentity identity, UUID emergencyId);
    void heartbeat(AuthenticatedIdentity identity, UUID emergencyId, LocationInput input);
    void recordLocation(AuthenticatedIdentity identity, UUID emergencyId, LocationInput input);
    void finish(AuthenticatedIdentity identity, UUID emergencyId);
    PageResponse<EmergencyResponse> ownHistory(AuthenticatedIdentity identity, int page, int size);

    /** Called by the administration module in HU-API-017; it owns neither the route nor audit logging. */
    boolean startAttention(AuthenticatedIdentity identity, UUID emergencyId);

    /** Called by the HU-API-018 scheduler after it has selected a possible timeout candidate. */
    void markOfflineIfTimedOut(UUID emergencyId);

    record CreationResult(EmergencyResponse emergency, boolean created) { }
}
