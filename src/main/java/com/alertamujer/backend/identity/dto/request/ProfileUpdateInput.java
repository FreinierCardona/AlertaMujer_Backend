package com.alertamujer.backend.identity.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Fields which a person may change directly; contacts have their own OTP flow. */
public record ProfileUpdateInput(
        @Pattern(regexp = "^@[a-z0-9](?:[a-z0-9_]{1,18}[a-z0-9])?$",
                message = "must be @ followed by 3 to 20 lowercase letters, numbers or underscores") String username,
        @Size(min = 1, max = 100) String firstNames,
        @Size(min = 1, max = 100) String lastNames) {

    public boolean hasChanges() {
        return username != null || firstNames != null || lastNames != null;
    }
}
