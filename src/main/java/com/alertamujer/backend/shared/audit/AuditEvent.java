package com.alertamujer.backend.shared.audit;

import java.util.Map;
import java.util.UUID;

/** A deliberately small, sanitized event accepted by the immutable audit log. */
public record AuditEvent(
        UUID actorUserId,
        UUID subjectUserId,
        String action,
        String entityType,
        UUID entityId,
        String result,
        Map<String, String> previousState,
        Map<String, String> newState,
        String description) {

    public static AuditEvent success(UUID actorUserId, UUID subjectUserId, String action,
            String entityType, UUID entityId, Map<String, String> previousState,
            Map<String, String> newState, String description) {
        return new AuditEvent(actorUserId, subjectUserId, action, entityType, entityId,
                "SUCCESS", previousState, newState, description);
    }
}
