package com.alertamujer.backend.emergency.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** La propietaria debe confirmar explícitamente que está a salvo antes de cerrar el SOS. */
public record EmergencyFinishInput(@NotNull @AssertTrue Boolean confirmedSafe) {
}
