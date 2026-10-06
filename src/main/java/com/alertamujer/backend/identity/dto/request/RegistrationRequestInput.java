package com.alertamujer.backend.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public data required to start a self-registration request. */
public record RegistrationRequestInput(
        @NotBlank @Pattern(regexp = "^@[a-z0-9](?:[a-z0-9_]{1,18}[a-z0-9])$",
                message = "must be @ followed by 3 to 20 lowercase letters, numbers or underscores")
        String username,
        @NotBlank @Size(max = 100) String firstNames,
        @NotBlank @Size(max = 100) String lastNames,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "^3[0-9]{9}$", message = "must be a normalized Colombian mobile number")
        String phone,
        @NotBlank @Size(min = 12, max = 255)
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{12,}$",
                message = "must contain an uppercase letter, a number and a symbol")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @AssertTrue(message = "must be accepted") Boolean acceptedTerms) {
}
