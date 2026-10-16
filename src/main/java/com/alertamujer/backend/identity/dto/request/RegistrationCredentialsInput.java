package com.alertamujer.backend.identity.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Credentials accepted only after the simulated-phone verification succeeds. */
public record RegistrationCredentialsInput(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 255)
        @Pattern(regexp = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{12,}$",
                message = "must contain an uppercase letter, a number and a symbol")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        @AssertTrue(message = "must be accepted") Boolean acceptedTerms) {
}
