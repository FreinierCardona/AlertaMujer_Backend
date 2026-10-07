package com.alertamujer.backend.contacts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The contract lets a caller identify only the intended directory user. */
public record ContactInvitationInput(
        @NotBlank
        @Pattern(regexp = "^@[a-z0-9](?:[a-z0-9_]{1,18}[a-z0-9])$") String username) {
}
