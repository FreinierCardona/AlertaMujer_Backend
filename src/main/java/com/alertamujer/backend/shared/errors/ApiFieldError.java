package com.alertamujer.backend.shared.errors;

/**
 * Describe un campo de entrada inválido sin reproducir el valor enviado.
 */
public record ApiFieldError(String field, String message) {
}
