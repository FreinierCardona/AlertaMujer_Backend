package com.alertamujer.backend.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Contract body for the authenticated device-token registration route. */
public record DeviceTokenInput(
        @NotBlank String token,
        @NotNull Platform platform) {

    public enum Platform { ANDROID }
}
