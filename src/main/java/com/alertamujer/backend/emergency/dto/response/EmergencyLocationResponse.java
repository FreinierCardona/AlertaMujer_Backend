package com.alertamujer.backend.emergency.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** Última ubicación aceptada por el servidor para una emergencia. */
public record EmergencyLocationResponse(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
        Instant capturedAt, Instant receivedAt) {
}
