package com.alertamujer.backend.evidence.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Metadatos públicos de evidencia. Las referencias internas de almacenamiento nunca salen del módulo. */
public record EvidenceResponse(UUID evidenceId, String mimeType, int sizeBytes, Instant receivedAt) { }
