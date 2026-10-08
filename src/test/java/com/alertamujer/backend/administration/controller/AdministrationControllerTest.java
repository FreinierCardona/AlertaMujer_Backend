package com.alertamujer.backend.administration.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.administration.dto.response.DashboardResponse;
import com.alertamujer.backend.administration.service.AdministrationService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
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

class AdministrationControllerTest {
    private AdministrationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AdministrationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdministrationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter())
                .setCustomArgumentResolvers(identityResolver()).build();
    }

    @Test
    void returnsTheMinimalDashboardContract() throws Exception {
        org.mockito.Mockito.when(service.dashboard(any())).thenReturn(new DashboardResponse(2, 3, 1));
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeCount").value(2))
                .andExpect(jsonPath("$.inProgressCount").value(3)).andExpect(jsonPath("$.offlineCount").value(1));
        verify(service).dashboard(any());
    }

    @Test
    void rejectsInvalidAccountStatusBeforeCallingTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ENTITY_ADMIN\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void forwardsAValidAccountStatusChange() throws Exception {
        UUID userId = UUID.randomUUID();
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/status", userId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        verify(service).changeStatus(any(), eq(userId), eq("DISABLED"));
    }

    private HandlerMethodArgumentResolver identityResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == AuthenticatedIdentity.class;
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID(), "ENTITY_ADMIN", false);
            }
        };
    }
}
