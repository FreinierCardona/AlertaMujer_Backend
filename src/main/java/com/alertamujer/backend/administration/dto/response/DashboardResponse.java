package com.alertamujer.backend.administration.dto.response;

/** Minimal operational counters defined by the REST contract. */
public record DashboardResponse(long activeCount, long inProgressCount, long offlineCount) {
}
