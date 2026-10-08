package com.alertamujer.backend.administration.dto.response;

import java.time.Instant;
import java.util.UUID;

/** The contract intentionally omits audit states, descriptions and personal data. */
public record AuditLogResponse(UUID auditLogId, String action, Instant createdAt) {
}
