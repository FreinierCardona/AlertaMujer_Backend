package com.alertamujer.backend.contacts.dto.response;

import java.util.List;

/** Estructura de respuesta compartida por las dos consultas paginadas de contactos. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
