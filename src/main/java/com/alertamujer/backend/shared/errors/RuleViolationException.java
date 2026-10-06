package com.alertamujer.backend.shared.errors;

public class RuleViolationException extends RuntimeException {

    public RuleViolationException() {
        super("The request does not satisfy a business rule.");
    }
}
