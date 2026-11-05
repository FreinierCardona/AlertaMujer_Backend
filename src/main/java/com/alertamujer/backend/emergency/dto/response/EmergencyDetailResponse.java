package com.alertamujer.backend.emergency.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Detalle autorizado de una emergencia. Las respuestas de listado y creación se mantienen resumidas. */
public record EmergencyDetailResponse(UUID emergencyId, String status, String previousOperationalStatus,
        Instant startedAt, Instant lastHeartbeatAt, Instant finalizedAt, String messageSnapshot,
        EmergencyLocationResponse lastConfirmedLocation, OwnerIdentity owner) {
    public EmergencyDetailResponse(UUID emergencyId, String status, String previousOperationalStatus,
            Instant startedAt, Instant lastHeartbeatAt, Instant finalizedAt, String messageSnapshot,
            EmergencyLocationResponse lastConfirmedLocation) {
        this(emergencyId, status, previousOperationalStatus, startedAt, lastHeartbeatAt, finalizedAt,
                messageSnapshot, lastConfirmedLocation, null);
    }

    public record OwnerIdentity(String username, String firstNames, String lastNames, String phone, String email) { }

}
