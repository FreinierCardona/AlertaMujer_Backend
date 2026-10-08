package com.alertamujer.backend.evidence.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public evidence metadata. Internal storage references never leave the evidence module. */
public record EvidenceResponse(UUID evidenceId, String mimeType, int sizeBytes, Instant receivedAt) { }
