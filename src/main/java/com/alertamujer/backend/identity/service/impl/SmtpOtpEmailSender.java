package com.alertamujer.backend.identity.service.impl;

import com.alertamujer.backend.identity.service.OtpEmailSender;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP implementation for the email channel. Credentials stay in the environment. */
@Component
class SmtpOtpEmailSender implements OtpEmailSender {

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final JavaMailSender mailSender;
    private final String from;

    SmtpOtpEmailSender(JavaMailSender mailSender, @Value("${alertamujer.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerificationCode(String destination, String code, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destination);
        message.setSubject("AlertaMujer - Verifica tu correo");
        message.setText("Hola,\n\nTu código de verificación de AlertaMujer es: " + code
                + "\n\nVence el " + EXPIRY_FORMAT.format(expiresAt)
                + ". No compartas este código.\n\nEquipo ProyectoAlertaMujer");
        mailSender.send(message);
    }
}
