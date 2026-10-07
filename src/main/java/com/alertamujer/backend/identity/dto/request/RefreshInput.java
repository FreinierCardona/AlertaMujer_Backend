package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Opaque refresh token supplied only to the renewal endpoint. */
public record RefreshInput(@NotBlank String refreshToken) {
}
