package com.alertamujer.backend.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.identity.dto.response.PhoneFirstRegistrationResponse;
import com.alertamujer.backend.identity.dto.response.RegistrationStageResponse;
import com.alertamujer.backend.identity.service.PhoneFirstRegistrationService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PhoneFirstRegistrationControllerTest {

    private PhoneFirstRegistrationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(PhoneFirstRegistrationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PhoneFirstRegistrationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void startsWithPersonalDataAndReturnsTheOpaqueProofOnlyInTheInitialResponse() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(service.start(any())).thenReturn(new PhoneFirstRegistrationResponse(
                requestId, "PHONE_PENDING", "opaque-proof", Instant.parse("2026-10-16T21:00:00Z"), "123456"));

        mockMvc.perform(post("/api/v2/registration-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"@ana_user","firstNames":"Ana","lastNames":"Perez","phone":"3001234567"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationRequestId").value(requestId.toString()))
                .andExpect(jsonPath("$.stage").value("PHONE_PENDING"))
                .andExpect(jsonPath("$.registrationProof").value("opaque-proof"));

        verify(service).start(any());
    }

    @Test
    void requiresTheOpaqueProofBeforeCredentialsAreAccepted() throws Exception {
        UUID requestId = UUID.randomUUID();
        mockMvc.perform(put("/api/v2/registration-requests/{id}/credentials", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com","password":"SecurePass#2026","acceptedTerms":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendsTheProofOnlyToTheV2ServiceAndNeverAsAnOtpBodyField() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(service.issueCode(eq(requestId), eq("opaque-proof"), any()))
                .thenReturn(new RegistrationStageResponse("PHONE_PENDING", Instant.parse("2026-10-16T21:00:00Z"), "123456"));

        mockMvc.perform(post("/api/v2/registration-requests/{id}/verification-codes", requestId)
                        .header("X-Registration-Proof", "opaque-proof")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"PHONE_SIMULATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("PHONE_PENDING"));

        verify(service).issueCode(eq(requestId), eq("opaque-proof"), any());
    }
}
