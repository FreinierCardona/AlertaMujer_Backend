package com.alertamujer.backend.identity.dto.response;

import java.util.UUID;

/** Representación segura de una solicitud de registro pendiente. */
public record RegistrationRequestResponse(UUID registrationRequestId, String status) {
}
