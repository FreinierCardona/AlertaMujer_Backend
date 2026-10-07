package com.alertamujer.backend.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.identity.dto.response.OtpIssuedResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationVerificationResponse;
import com.alertamujer.backend.identity.model.OtpChannel;
import com.alertamujer.backend.identity.service.OtpService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OtpControllerTest {

    private OtpService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(OtpService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OtpController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void returnsSmsCodeOnlyForTheAcademicSimulation() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(service.issueRegistrationCode(requestId, OtpChannel.SMS))
                .thenReturn(new OtpIssuedResponse(Instant.parse("2026-10-06T21:00:00Z"), "123456"));

        mockMvc.perform(post("/api/v1/registration-requests/{id}/verification-codes", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"SMS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulatedSmsCode").value("123456"));

        verify(service).issueRegistrationCode(requestId, OtpChannel.SMS);
    }

    @Test
    void rejectsMalformedOtpBeforeTheServiceAndKeepsPasswordResetGeneric() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/registration-requests/{id}/verification-codes/verify", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"EMAIL\",\"code\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/auth/password-reset/verification-codes")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(service).requestPasswordResetCode("missing@example.com");
        verify(service, org.mockito.Mockito.never()).verifyRegistrationCode(any(), any(), any());
    }

    @Test
    void returnsCompletionStateWithoutExposingSecrets() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(service.verifyRegistrationCode(requestId, OtpChannel.EMAIL, "123456"))
                .thenReturn(new RegistrationVerificationResponse("COMPLETED", userId));

        mockMvc.perform(post("/api/v1/registration-requests/{id}/verification-codes/verify", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.codeHash").doesNotExist());

        verify(service).verifyRegistrationCode(requestId, OtpChannel.EMAIL, "123456");
    }
}
