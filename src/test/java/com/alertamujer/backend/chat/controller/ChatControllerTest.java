package com.alertamujer.backend.chat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.chat.dto.response.ChatMessageResponse;
import com.alertamujer.backend.chat.service.ChatService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
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

class ChatControllerTest {
    private ChatService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ChatService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(identityResolver()).addFilters(new RequestIdFilter()).build();
    }

    @Test
    void returnsPersistedMessagesAfterTheConfirmedServerIdentifier() throws Exception {
        UUID emergencyId = UUID.randomUUID();
        UUID clientMessageId = UUID.randomUUID();
        UUID senderUserId = UUID.randomUUID();
        when(service.list(any(), eq(emergencyId), eq(40L), eq(20))).thenReturn(List.of(
                new ChatMessageResponse(41L, clientMessageId, senderUserId, "USER", "Help",
                        Instant.parse("2026-10-07T18:00:00Z"))));

        mockMvc.perform(get("/api/v1/emergencies/{emergencyId}/messages", emergencyId).param("after", "40"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].messageId").value(41))
                .andExpect(jsonPath("$[0].clientMessageId").value(clientMessageId.toString()))
                .andExpect(jsonPath("$[0].senderUserId").value(senderUserId.toString()))
                .andExpect(jsonPath("$[0].senderRole").value("USER"))
                .andExpect(jsonPath("$[0].content").value("Help"));
        verify(service).list(any(), eq(emergencyId), eq(40L), eq(20));
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
