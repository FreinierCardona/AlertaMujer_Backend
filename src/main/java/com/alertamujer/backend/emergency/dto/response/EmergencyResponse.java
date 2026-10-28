package com.alertamujer.backend.emergency.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Estado público del SOS. La copia del mensaje y la ubicación siguen siendo datos internos. */
public record EmergencyResponse(UUID emergencyId, String status, String previousOperationalStatus,
        Instant startedAt, Instant lastHeartbeatAt, Instant finalizedAt, String username, String displayName) {
    public EmergencyResponse(UUID emergencyId, String status, String previousOperationalStatus,
            Instant startedAt, Instant lastHeartbeatAt, Instant finalizedAt) {
        this(emergencyId, status, previousOperationalStatus, startedAt, lastHeartbeatAt, finalizedAt, null, null);
    }
}
