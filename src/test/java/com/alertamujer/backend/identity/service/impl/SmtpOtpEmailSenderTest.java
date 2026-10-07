package com.alertamujer.backend.identity.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpOtpEmailSenderTest {

    @Test
    void buildsThePersonalizedAlertamujerEmailWithoutExposingItThroughAnApiResponse() {
        JavaMailSender mailSender = Mockito.mock(JavaMailSender.class);
        SmtpOtpEmailSender sender = new SmtpOtpEmailSender(mailSender, "no-reply@alertamujer.example");

        sender.sendVerificationCode("cardonafreinier@gmail.com", "123456", Instant.parse("2026-10-06T21:00:00Z"));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("no-reply@alertamujer.example");
        assertThat(message.getValue().getTo()).containsExactly("cardonafreinier@gmail.com");
        assertThat(message.getValue().getSubject()).contains("AlertaMujer");
        assertThat(message.getValue().getText()).contains("123456", "ProyectoAlertaMujer", "2026-10-06 21:00 UTC");
    }
}
