package com.alertamujer.backend.shared.errors;

import java.util.List;
import java.util.UUID;

/**
 * Public error shape defined by the REST contract. It intentionally contains no
 * exception details or request values.
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
