package com.alertamujer.backend.shared.errors;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Authentication is required.");
    }
}
