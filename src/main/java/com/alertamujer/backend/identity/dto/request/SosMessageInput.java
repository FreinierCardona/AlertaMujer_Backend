package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A personal SOS message. The service persists its trimmed representation. */
public record SosMessageInput(@NotBlank @Size(max = 500) String message) {
}
