package com.alertamujer.backend.identity.dto.response;

import java.time.Instant;

/** Respuesta segura v2 tras emitir un OTP o cambiar de etapa. */
public record RegistrationStageResponse(String stage, Instant expiresAt, String simulatedPhoneCode) {
}
