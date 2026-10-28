package com.alertamujer.backend.identity.dto.response;

import java.util.UUID;

/** Estado seguro después de consumir el OTP de un canal de registro. */
public record RegistrationVerificationResponse(String status, UUID userId) {
}
