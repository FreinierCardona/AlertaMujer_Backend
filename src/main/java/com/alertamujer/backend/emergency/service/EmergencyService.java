package com.alertamujer.backend.emergency.service;

import com.alertamujer.backend.emergency.dto.request.EmergencyCreateInput;
import com.alertamujer.backend.emergency.dto.request.LocationInput;
import com.alertamujer.backend.emergency.dto.response.EmergencyDetailResponse;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageResponse;
import java.util.UUID;

/** Creación, recuperación y consulta de SOS establecidas por HU-API-012. */
public interface EmergencyService {
    CreationResult createOrRecover(AuthenticatedIdentity identity, EmergencyCreateInput input);
    EmergencyResponse active(AuthenticatedIdentity identity);
    EmergencyDetailResponse ownEmergency(AuthenticatedIdentity identity, UUID emergencyId);
    void heartbeat(AuthenticatedIdentity identity, UUID emergencyId, LocationInput input);
    void recordLocation(AuthenticatedIdentity identity, UUID emergencyId, LocationInput input);
    void finish(AuthenticatedIdentity identity, UUID emergencyId);
    PageResponse<EmergencyResponse> ownHistory(AuthenticatedIdentity identity, int page, int size);

    /** Invocado por el módulo de administración en HU-API-017; no gestiona la ruta ni el registro de auditoría. */
    boolean startAttention(AuthenticatedIdentity identity, UUID emergencyId);

    /** Invocado por el planificador de HU-API-018 tras seleccionar un posible candidato por tiempo de espera agotado. */
    void markOfflineIfTimedOut(UUID emergencyId);

    record CreationResult(EmergencyResponse emergency, boolean created) { }
}
