package com.alertamujer.backend.notification.fcm;

/** Only provider-acknowledged outcomes are returned to the dispatch service. */
public record FcmResult(Status status, String providerMessageId, String errorCode) {
    public enum Status { SENT_TO_FCM, FAILED, INVALID_TOKEN }

    public static FcmResult sent(String providerMessageId) {
        return new FcmResult(Status.SENT_TO_FCM, providerMessageId, null);
    }
    public static FcmResult failed(String errorCode) {
        return new FcmResult(Status.FAILED, null, errorCode);
    }
    public static FcmResult invalidToken(String errorCode) {
        return new FcmResult(Status.INVALID_TOKEN, null, errorCode);
    }
}
