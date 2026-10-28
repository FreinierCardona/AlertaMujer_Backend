package com.alertamujer.backend.shared.errors;

import java.util.List;
import java.util.UUID;

/**
 * Estructura pública de error definida por el contrato REST. No contiene detalles de excepciones ni
 * valores de la solicitud.
 */
public record ApiError(
        String code,
        String message,
        UUID requestId,
        List<ApiFieldError> fields) {

    public ApiError(String code, String message, UUID requestId) {
        this(code, message, requestId, null);
    }
}
