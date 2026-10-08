package com.alertamujer.backend.chat.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alertamujer.backend.chat.service.ChatService;
import com.alertamujer.backend.identity.service.AuthenticationService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.security.IdentityAuthentication;
import com.alertamujer.backend.shared.security.JwtAccessTokenService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;

class StompAuthenticationInterceptorTest {
    private JwtAccessTokenService jwt;
    private AuthenticationService authentication;
    private ChatService chat;
    private StompAuthenticationInterceptor interceptor;
    private AuthenticatedIdentity identity;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtAccessTokenService.class);
        authentication = mock(AuthenticationService.class);
        chat = mock(ChatService.class);
        interceptor = new StompAuthenticationInterceptor(jwt, authentication, chat);
        identity = new AuthenticatedIdentity(UUID.randomUUID(), UUID.randomUUID(), "USER", false);
    }

    @Test
    void connectUsesTheSameDecodedTokenAndLiveSessionValidationAsRest() {
        Jwt token = mock(Jwt.class);
        when(jwt.decode("access-token")).thenReturn(token);
        when(token.getSubject()).thenReturn(identity.userId().toString());
        when(token.getClaimAsString("sid")).thenReturn(identity.sessionId().toString());
        when(token.getClaimAsString("role")).thenReturn("USER");
        when(authentication.validateAccessSession(identity.userId(), identity.sessionId(), "USER")).thenReturn(identity);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer access-token");

        Message<?> result = interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isInstanceOf(IdentityAuthentication.class);
        verify(authentication).validateAccessSession(identity.userId(), identity.sessionId(), "USER");
    }

    @Test
    void authorizesOnlyTheExactEmergencyTopicForSubscriptions() {
        UUID emergencyId = UUID.randomUUID();
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/emergencies/" + emergencyId);
        headers.setUser(new IdentityAuthentication(identity));

        interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);

        verify(chat).authorizeSubscription(identity, emergencyId);
    }

    @Test
    void rejectsGlobalTopicsBeforeTheyCanReachTheBroker() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/emergencies/*");
        headers.setUser(new IdentityAuthentication(identity));

        assertThatThrownBy(() -> interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null))
                .isInstanceOf(ForbiddenException.class);
    }
}
