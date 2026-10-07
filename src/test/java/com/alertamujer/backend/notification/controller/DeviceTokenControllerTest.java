package com.alertamujer.backend.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.notification.service.NotificationService;
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

class DeviceTokenControllerTest {
    private NotificationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(NotificationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DeviceTokenController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(identityResolver())
                .addFilters(new RequestIdFilter()).build();
    }

    @Test
    void returnsCreatedForANewTokenAndNeverEchoesIt() throws Exception {
        when(service.registerDeviceToken(any(), any())).thenReturn(true);
        mockMvc.perform(post("/api/v1/me/device-tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"private-token\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isCreated());
        verify(service).registerDeviceToken(any(), any());
    }

    @Test
    void rejectsMissingOrUnsupportedContractFieldsBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/me/device-tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest());
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
