package com.alertamujer.backend.emergency.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** The owner must explicitly confirm that she is safe before closing an SOS. */
public record EmergencyFinishInput(@NotNull @AssertTrue Boolean confirmedSafe) {
}
