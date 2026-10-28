package com.alertamujer.backend.contacts.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Representación mínima de una emergencia abierta visible para un contacto autorizado. */
public record ContactAlertResponse(UUID emergencyId, String title, String status, String userDisplayName,
        String message, Instant startedAt) { }
