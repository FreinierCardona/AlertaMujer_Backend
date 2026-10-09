package com.alertamujer.backend.emergency.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.emergency.dto.response.EmergencyDetailResponse;
import com.alertamujer.backend.emergency.dto.response.EmergencyLocationResponse;
import com.alertamujer.backend.emergency.dto.response.EmergencyResponse;
import java.math.BigDecimal;
import com.alertamujer.backend.emergency.service.EmergencyService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class EmergencyControllerTest {

    private EmergencyService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EmergencyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new EmergencyController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(identityResolver()).addFilters(new RequestIdFilter()).build();
    }

    @Test
    void returnsCreatedOnlyWhenTheSosWasPersistedForTheFirstTime() throws Exception {
        UUID emergencyId = UUID.randomUUID();
        EmergencyResponse emergency = new EmergencyResponse(emergencyId, "ACTIVE", null,
                Instant.parse("2026-10-07T18:00:00Z"), null, null);
        when(service.createOrRecover(any(), any())).thenReturn(new EmergencyService.CreationResult(emergency, true));

        mockMvc.perform(post("/api/v1/emergencies").contentType(MediaType.APPLICATION_JSON).content("""
                {"latitude":4.609710,"longitude":-74.081750,"accuracyMeters":8.5,
                  "capturedAt":"2026-10-07T17:59:58Z"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emergencyId").value(emergencyId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(service).createOrRecover(any(), any());
    }

    @Test
    void returnsOkWhenTheSameOpenSosIsRecovered() throws Exception {
        EmergencyResponse emergency = new EmergencyResponse(UUID.randomUUID(), "ACTIVE", null,
                Instant.parse("2026-10-07T18:00:00Z"), null, null);
        when(service.createOrRecover(any(), any())).thenReturn(new EmergencyService.CreationResult(emergency, false));

        mockMvc.perform(post("/api/v1/emergencies").contentType(MediaType.APPLICATION_JSON).content("""
                {"latitude":4.609710,"longitude":-74.081750,"capturedAt":"2026-10-07T17:59:58Z"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emergencyId").value(emergency.emergencyId().toString()));
    }

    @Test
    void rejectsCoordinatesOutsideTheContractRangeBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/emergencies").contentType(MediaType.APPLICATION_JSON).content("""
                {"latitude":91,"longitude":-74.081750,"capturedAt":"2026-10-07T17:59:58Z"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void acceptsHeartbeatAndLocationUsingTheContractStatusCodes() throws Exception {
        UUID emergencyId = UUID.randomUUID();
        String body = """
                {"latitude":4.609710,"longitude":-74.081750,"accuracyMeters":8.5,
                  "capturedAt":"2026-10-07T17:59:58Z"}
                """;

        mockMvc.perform(post("/api/v1/emergencies/{emergencyId}/heartbeats", emergencyId)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/emergencies/{emergencyId}/locations", emergencyId)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        verify(service).heartbeat(any(), org.mockito.ArgumentMatchers.eq(emergencyId), any());
        verify(service).recordLocation(any(), org.mockito.ArgumentMatchers.eq(emergencyId), any());
    }

    @Test
    void returnsAuthorizedDetailWithTheSnapshotAndLastConfirmedLocation() throws Exception {
        UUID emergencyId = UUID.randomUUID();
        EmergencyDetailResponse detail = new EmergencyDetailResponse(emergencyId, "ACTIVE", null,
                Instant.parse("2026-10-07T18:00:00Z"), Instant.parse("2026-10-07T18:01:00Z"), null,
                "Necesito ayuda", new EmergencyLocationResponse(new BigDecimal("4.609710"),
                        new BigDecimal("-74.081750"), new BigDecimal("8.5"),
                        Instant.parse("2026-10-07T18:00:50Z"), Instant.parse("2026-10-07T18:01:00Z")));
        when(service.ownEmergency(any(), org.mockito.ArgumentMatchers.eq(emergencyId))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/emergencies/{emergencyId}", emergencyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageSnapshot").value("Necesito ayuda"))
                .andExpect(jsonPath("$.lastConfirmedLocation.latitude").value(4.609710))
                .andExpect(jsonPath("$.lastConfirmedLocation.capturedAt").value("2026-10-07T18:00:50Z"));

        verify(service).ownEmergency(any(), org.mockito.ArgumentMatchers.eq(emergencyId));
    }

    @Test
    void requiresAnExplicitSafeConfirmationBeforeFinishing() throws Exception {
        mockMvc.perform(post("/api/v1/emergencies/{emergencyId}/finish", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{" + "\"confirmedSafe\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
