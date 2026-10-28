package com.alertamujer.backend.contacts.dto.response;

/** Excluye intencionalmente el correo y el teléfono. */
public record DirectoryUserResponse(String username, String firstNames, String lastNames) {
}
