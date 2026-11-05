package com.alertamujer.backend.identity.service;

import java.time.Instant;

/** Interfaz para entregar un OTP real por EMAIL sin exponerlo en registros ni en la API. */
public interface OtpEmailSender {

    void sendVerificationCode(String destination, String code, Instant expiresAt);
}
