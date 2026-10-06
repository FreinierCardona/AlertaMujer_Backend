package com.alertamujer.backend.shared.validation;

import java.util.List;

/**
 * Shared response shape for paginated REST queries.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
