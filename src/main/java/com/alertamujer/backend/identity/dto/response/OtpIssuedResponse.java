package com.alertamujer.backend.identity.dto.response;

import java.time.Instant;

/**
 * El valor SMS existe solo para la simulación académica aprobada. Permanece nulo para EMAIL y nunca se
 * almacena en texto plano.
 */
public record OtpIssuedResponse(Instant expiresAt, String simulatedSmsCode) {
}
