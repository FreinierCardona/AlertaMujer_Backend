package com.alertamujer.backend.shared.errors;

/**
 * Describes an invalid input field without echoing its submitted value.
 */
public record ApiFieldError(String field, String message) {
}
