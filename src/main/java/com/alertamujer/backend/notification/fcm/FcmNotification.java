package com.alertamujer.backend.notification.fcm;

import java.math.BigDecimal;
import java.util.UUID;

/** Data sent to FCM; it is deliberately never logged by this module. */
public record FcmNotification(UUID emergencyId, String token, String message, BigDecimal latitude, BigDecimal longitude) {
}
