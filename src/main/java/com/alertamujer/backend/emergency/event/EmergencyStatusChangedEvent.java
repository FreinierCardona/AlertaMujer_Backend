package com.alertamujer.backend.emergency.event;

import java.time.Instant;
import java.util.UUID;

/** Transición SOS persistida que solo puede anunciarse después de confirmar su transacción. */
public record EmergencyStatusChangedEvent(UUID emergencyId, String status, Instant occurredAt) { }
