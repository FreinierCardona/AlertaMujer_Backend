package com.alertamujer.backend.evidence.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.evidence.dto.response.EvidenceResponse;
import com.alertamujer.backend.evidence.service.EvidenceService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class EvidenceControllerTest {
    private EvidenceService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EvidenceService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EvidenceController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(identityResolver())
                .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void exposesOnlyPublicMetadataAndAProtectedWebpStream() throws Exception {
        UUID emergencyId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        EvidenceResponse metadata = new EvidenceResponse(evidenceId, "image/webp", 4, Instant.parse("2026-10-07T18:00:00Z"));
        when(service.upload(any(), any(), any())).thenReturn(metadata);
        when(service.list(any(), any())).thenReturn(List.of(metadata));
        when(service.content(any(), any())).thenReturn(new EvidenceService.EvidenceContent(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), 4));

        mockMvc.perform(multipart("/api/v1/emergencies/{emergencyId}/evidences", emergencyId)
                .file(new MockMultipartFile("file", "camera.jpg", "image/jpeg", new byte[] {1, 2})))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.evidenceId").value(evidenceId.toString()))
                .andExpect(jsonPath("$.fileReference").doesNotExist());
        mockMvc.perform(get("/api/v1/emergencies/{emergencyId}/evidences", emergencyId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].mimeType").value("image/webp"));
        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/content", evidenceId)).andExpect(status().isOk())
                .andExpect(content().contentType("image/webp")).andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
        verify(service).upload(any(), any(), any());
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
