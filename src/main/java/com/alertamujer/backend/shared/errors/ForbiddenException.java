package com.alertamujer.backend.shared.errors;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Access is denied.");
    }
}
