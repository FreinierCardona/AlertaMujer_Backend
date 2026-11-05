package com.alertamujer.backend.shared.errors;

public class StateConflictException extends RuntimeException {

    public StateConflictException() {
        super("The requested operation conflicts with the current state.");
    }
}
