package com.alertamujer.backend.identity.dto.request;

import com.alertamujer.backend.identity.model.OtpChannel;
import jakarta.validation.constraints.NotNull;

/** Chooses the already-defined registration verification channel. */
public record ChannelRequest(@NotNull OtpChannel channel) {
}
