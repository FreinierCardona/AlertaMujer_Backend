package com.alertamujer.backend.contacts.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.contacts.dto.response.ContactAlertResponse;
import com.alertamujer.backend.contacts.service.ContactAlertService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.validation.PageResponse;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ContactAlertControllerTest {
    private ContactAlertService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ContactAlertService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContactAlertController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(identityResolver())
                .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void exposesThePagedMinimalListAndRejectsAnInvalidPageSize() throws Exception {
        UUID emergencyId = UUID.randomUUID();
        when(service.list(any(), eq(0), eq(20))).thenReturn(new PageResponse<>(List.of(new ContactAlertResponse(emergencyId,
                "Alerta de Emergencia", "ACTIVE", "Ana Perez", "Ana Perez está en peligro", Instant.parse("2026-10-15T12:00:00Z"))),
                0, 20, 1));

        mockMvc.perform(get("/api/v1/me/contact-alerts"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].emergencyId").value(emergencyId.toString()))
                .andExpect(jsonPath("$.items[0].message").value("Ana Perez está en peligro"));
        mockMvc.perform(get("/api/v1/me/contact-alerts?size=51"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(service).list(any(), eq(0), eq(20));
    }

    @Test
    void streamsOnlyWebpWithNoStore() throws Exception {
        UUID emergencyId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        when(service.evidenceContent(any(), eq(emergencyId), eq(evidenceId)))
                .thenReturn(new ContactAlertService.EvidenceContent(new ByteArrayInputStream(new byte[] {1, 2}), 2));

        mockMvc.perform(get("/api/v1/me/contact-alerts/{emergencyId}/evidences/{evidenceId}/content", emergencyId, evidenceId))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Type", "image/webp"));
    }

    private HandlerMethodArgumentResolver identityResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == AuthenticatedIdentity.class;
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID(), "USER", false);
            }
        };
    }
}
