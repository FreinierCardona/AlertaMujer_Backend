package com.alertamujer.backend.contacts.dto.response;

import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only detail deliberately limited to information needed by an emergency contact. */
public record ContactAlertDetailResponse(UUID emergencyId, String status, String userDisplayName, Instant startedAt,
        String messageSnapshot, LastConfirmedLocation lastConfirmedLocation, List<EvidenceResponse> evidences) {
    public record LastConfirmedLocation(BigDecimal latitude, BigDecimal longitude, Instant capturedAt) { }
}
