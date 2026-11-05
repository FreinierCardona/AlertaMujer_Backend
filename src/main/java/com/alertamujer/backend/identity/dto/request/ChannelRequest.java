package com.alertamujer.backend.identity.dto.request;

import com.alertamujer.backend.identity.model.OtpChannel;
import jakarta.validation.constraints.NotNull;

/** Selecciona el canal de verificación de registro ya definido. */
public record ChannelRequest(@NotNull OtpChannel channel) {
}
