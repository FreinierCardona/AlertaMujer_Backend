package com.alertamujer.backend.administration.dto.response;

/** Contadores operativos mínimos definidos por el contrato REST. */
public record DashboardResponse(long activeCount, long inProgressCount, long offlineCount, long finalizedCount) {
}
