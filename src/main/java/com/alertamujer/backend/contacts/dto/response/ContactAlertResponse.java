package com.alertamujer.backend.contacts.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Minimal open emergency projection visible to an authorized emergency contact. */
public record ContactAlertResponse(UUID emergencyId, String title, String status, String userDisplayName,
        String message, Instant startedAt) { }
