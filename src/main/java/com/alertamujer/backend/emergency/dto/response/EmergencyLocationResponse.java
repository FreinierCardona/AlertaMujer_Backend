package com.alertamujer.backend.emergency.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** Last location that the Backend has accepted for an emergency. */
public record EmergencyLocationResponse(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
        Instant capturedAt, Instant receivedAt) {
}
