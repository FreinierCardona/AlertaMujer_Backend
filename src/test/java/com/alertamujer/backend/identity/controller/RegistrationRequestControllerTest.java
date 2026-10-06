package com.alertamujer.backend.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.identity.dto.response.RegistrationRequestResponse;
import com.alertamujer.backend.identity.service.RegistrationRequestService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegistrationRequestControllerTest {

    private RegistrationRequestService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(RegistrationRequestService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RegistrationRequestController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void createsAPublicRequestAndReturnsOnlyTheSafeContract() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(service.startPublicRegistration(any()))
                .thenReturn(new RegistrationRequestResponse(requestId, "PENDING"));

        mockMvc.perform(post("/api/v1/registration-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"@ana_user","firstNames":"Ana","lastNames":"Perez",
                                "email":"ana@example.com","phone":"3001234567",
                                "password":"SecurePass#2026","acceptedTerms":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationRequestId").value(requestId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(service).startPublicRegistration(any());
    }

    @Test
    void rejectsInvalidPasswordAndMissingTermsBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/registration-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"@ab","firstNames":"Ana","lastNames":"Perez",
                                "email":"ana@example.com","phone":"3001234567",
                                "password":"weak","acceptedTerms":false}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(service, org.mockito.Mockito.never()).startPublicRegistration(any());
    }
}
