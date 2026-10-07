package com.alertamujer.backend.emergency.service;

import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.util.UUID;

/** SOS creation/recovery and reads established by HU-API-012. */
public interface EmergencyService {
    CreationResult createOrRecover(AuthenticatedIdentity identity, EmergencyCreateInput input);
    EmergencyResponse active(AuthenticatedIdentity identity);
    EmergencyResponse ownEmergency(AuthenticatedIdentity identity, UUID emergencyId);

    record CreationResult(EmergencyResponse emergency, boolean created) { }
}
