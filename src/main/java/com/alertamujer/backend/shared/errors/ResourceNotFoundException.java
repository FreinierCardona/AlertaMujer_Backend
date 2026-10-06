package com.alertamujer.backend.shared.errors;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("Resource was not found.");
    }
}
