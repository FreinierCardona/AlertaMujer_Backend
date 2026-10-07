package com.alertamujer.backend.notification.fcm;

/** The provider could not be contacted, so no delivery outcome is known and PENDING must remain. */
public class FcmUnavailableBeforeSendException extends RuntimeException {
    public FcmUnavailableBeforeSendException() { super("FCM is unavailable before a send can start."); }
}
