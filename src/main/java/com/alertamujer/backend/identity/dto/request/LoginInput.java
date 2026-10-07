package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Public credentials accepted by the login contract. */
public record LoginInput(@NotBlank String identifier, @NotBlank String password) {
}
