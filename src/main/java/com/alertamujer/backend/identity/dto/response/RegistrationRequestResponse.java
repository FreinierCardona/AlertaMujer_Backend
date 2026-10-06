package com.alertamujer.backend.identity.dto.response;

import java.util.UUID;

/** Safe representation of a pending registration request. */
public record RegistrationRequestResponse(UUID registrationRequestId, String status) {
}
