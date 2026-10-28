package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Token opaco de renovación entregado únicamente al punto de entrada de renovación. */
public record RefreshInput(@NotBlank String refreshToken) {
}
