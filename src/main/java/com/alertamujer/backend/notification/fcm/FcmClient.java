package com.alertamujer.backend.notification.fcm;

/** Minimal boundary around the external Firebase dependency. */
public interface FcmClient {
    FcmResult send(FcmNotification notification);
}
