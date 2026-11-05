package com.alertamujer.backend.emergency.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Ubicación inicial proporcionada cuando un usuario con rol USER activa el SOS. */
public record EmergencyCreateInput(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin("0.0") BigDecimal accuracyMeters,
        @NotNull Instant capturedAt,
        @Size(max = 500) String message) {
}
