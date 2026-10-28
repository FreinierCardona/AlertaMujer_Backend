package com.alertamujer.backend.shared.validation;

import java.util.List;

/**
 * Estructura de respuesta compartida para consultas REST paginadas.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
