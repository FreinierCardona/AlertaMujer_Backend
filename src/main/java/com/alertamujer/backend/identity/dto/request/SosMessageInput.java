package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mensaje SOS personal. El servicio lo guarda sin espacios al inicio ni al final. */
public record SosMessageInput(@NotBlank @Size(max = 500) String message) {
}
