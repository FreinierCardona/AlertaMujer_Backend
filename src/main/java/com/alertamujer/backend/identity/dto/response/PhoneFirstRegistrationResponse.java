package com.alertamujer.backend.identity.dto.response;

import java.time.Instant;
import java.util.UUID;

/** La prueba opaca se devuelve una sola vez y nunca se almacena en texto plano. */
public record PhoneFirstRegistrationResponse(
        UUID registrationRequestId,
        String stage,
        String registrationProof,
        Instant expiresAt,
        String simulatedPhoneCode) {
}
