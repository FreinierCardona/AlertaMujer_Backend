package com.alertamujer.backend.emergency.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Authorized detail for one emergency. List and create responses remain intentionally summarized. */
public record EmergencyDetailResponse(UUID emergencyId, String status, String previousOperationalStatus,
        Instant startedAt, Instant lastHeartbeatAt, Instant finalizedAt, String messageSnapshot,
        EmergencyLocationResponse lastConfirmedLocation) {
}
