package com.alertamujer.backend.contacts.dto.response;

/** Deliberately excludes email and phone. */
public record DirectoryUserResponse(String username, String firstNames, String lastNames) {
}
