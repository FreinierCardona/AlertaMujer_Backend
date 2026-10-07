package com.alertamujer.backend.emergency.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published inside the SOS transaction and consumed only after its successful commit. */
public record EmergencyCreatedEvent(UUID emergencyId, String message, BigDecimal latitude, BigDecimal longitude) {
}
