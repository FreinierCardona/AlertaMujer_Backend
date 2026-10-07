package com.alertamujer.backend.identity.service;

import java.time.Instant;

/** Boundary used to deliver a real EMAIL OTP without leaking it to logs/API. */
public interface OtpEmailSender {

    void sendVerificationCode(String destination, String code, Instant expiresAt);
}
