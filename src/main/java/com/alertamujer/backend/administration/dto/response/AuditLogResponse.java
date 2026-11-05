package com.alertamujer.backend.administration.dto.response;

import java.time.Instant;
import java.util.UUID;

/** El contrato omite intencionalmente estados de auditoría, descripciones y datos personales. */
public record AuditLogResponse(UUID auditLogId, String action, Instant createdAt) {
}
