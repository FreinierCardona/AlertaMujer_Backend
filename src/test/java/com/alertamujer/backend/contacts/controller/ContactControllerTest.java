package com.alertamujer.backend.contacts.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertamujer.backend.contacts.dto.response.ContactInvitationResponse;
import com.alertamujer.backend.contacts.dto.response.ContactResponse;
import com.alertamujer.backend.contacts.dto.response.PageResponse;
import com.alertamujer.backend.contacts.service.ContactService;
import com.alertamujer.backend.shared.errors.GlobalExceptionHandler;
import com.alertamujer.backend.shared.observability.RequestIdFilter;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

class ContactControllerTest {

    private ContactService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ContactService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContactController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(identityResolver())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void createsOnlyTheContractInvitationWithoutAcceptingCallerControlledState() throws Exception {
        UUID contactId = UUID.randomUUID();
        when(service.invite(any(), any())).thenReturn(new ContactService.InvitationResult(
                new ContactInvitationResponse(contactId, "PENDING", Instant.parse("2026-10-08T18:00:00Z")), true));

        mockMvc.perform(post("/api/v1/contact-invitations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"@bea\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactId").value(contactId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-08T18:00:00Z"));

        verify(service).invite(any(), any());
    }

    @Test
    void rejectsAnInvalidInvitationBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/v1/contact-invitations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ana\",\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsOversizedDirectoryAndContactPagesBeforeTheService() throws Exception {
        mockMvc.perform(get("/api/v1/directory").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/contacts").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(service, never()).directory(any(), any(), any(Integer.class), any(Integer.class));
        verify(service, never()).ownContacts(any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void returnsOnlySafeCounterpartPresentationAndBackendAuthorizedActions() throws Exception {
        UUID contactId = UUID.randomUUID();
        when(service.ownContacts(any(), any(Integer.class), any(Integer.class))).thenReturn(new PageResponse<>(List.of(
                new ContactResponse(contactId, "PENDING", Instant.parse("2026-10-08T18:00:00Z"), true,
                        new ContactResponse.Counterpart("@bea", "Bea", "Rojas"),
                        ContactResponse.Direction.RECEIVED,
                        List.of(ContactResponse.Action.ACCEPT, ContactResponse.Action.REJECT))), 0, 20, 1));

        mockMvc.perform(get("/api/v1/contacts").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].contactId").value(contactId.toString()))
                .andExpect(jsonPath("$.items[0].counterpart.username").value("@bea"))
                .andExpect(jsonPath("$.items[0].counterpart.firstNames").value("Bea"))
                .andExpect(jsonPath("$.items[0].counterpart.lastNames").value("Rojas"))
                .andExpect(jsonPath("$.items[0].direction").value("RECEIVED"))
                .andExpect(jsonPath("$.items[0].allowedActions[0]").value("ACCEPT"))
                .andExpect(jsonPath("$.items[0].allowedActions[1]").value("REJECT"))
                .andExpect(jsonPath("$.items[0].counterpart.email").doesNotExist())
                .andExpect(jsonPath("$.items[0].counterpart.phone").doesNotExist());
    }

    private HandlerMethodArgumentResolver identityResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == AuthenticatedIdentity.class;
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID(), "USER", false);
            }
        };
    }
}
