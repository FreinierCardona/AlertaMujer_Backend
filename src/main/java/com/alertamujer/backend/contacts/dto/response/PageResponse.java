package com.alertamujer.backend.contacts.dto.response;

import java.util.List;

/** Small shared response shape for the two paginated contact reads. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
