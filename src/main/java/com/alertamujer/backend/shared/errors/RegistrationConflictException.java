package com.alertamujer.backend.shared.errors;

public class RegistrationConflictException extends RuleViolationException {
    private final String field;

    public RegistrationConflictException(String field) {
        this.field = field;
    }

    public String field() { return field; }

    public String fieldMessage() {
        return switch (field) {
            case "username" -> "Este usuario ya está registrado o tiene una solicitud pendiente.";
            case "phone" -> "Este teléfono ya está registrado o tiene una solicitud pendiente.";
            default -> "Este correo ya está registrado o tiene una solicitud pendiente.";
        };
    }

    public static RuleViolationException fromConstraint(org.springframework.dao.DataIntegrityViolationException exception) {
        String detail = exception.getMostSpecificCause().getMessage();
        for (String field : java.util.List.of("username", "phone", "email")) {
            if (detail != null && (detail.contains("ux_registration_requests_pending_" + field)
                    || detail.contains("users_" + field + "_key"))) return new RegistrationConflictException(field);
        }
        return new RuleViolationException();
    }
}
