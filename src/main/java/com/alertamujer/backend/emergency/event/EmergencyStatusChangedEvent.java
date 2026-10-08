package com.alertamujer.backend.emergency.event;

import java.time.Instant;
import java.util.UUID;

/** Persisted SOS transition that can be announced only after its transaction commits. */
public record EmergencyStatusChangedEvent(UUID emergencyId, String status, Instant occurredAt) { }
