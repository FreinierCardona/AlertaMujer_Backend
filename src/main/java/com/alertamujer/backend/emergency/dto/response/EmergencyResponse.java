package com.alertamujer.backend.emergency.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public SOS state. The message snapshot and location remain internal data. */
public record EmergencyResponse(UUID emergencyId, String status, String previousOperationalStatus,
        Instant startedAt, Instant lastHeartbeatAt, Instant finalizedAt) {
}
