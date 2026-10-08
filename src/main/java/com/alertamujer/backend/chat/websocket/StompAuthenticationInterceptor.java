package com.alertamujer.backend.chat.websocket;

import com.alertamujer.backend.chat.service.ChatService;
import com.alertamujer.backend.identity.service.AuthenticationService;
import com.alertamujer.backend.shared.errors.ForbiddenException;
import com.alertamujer.backend.shared.errors.ResourceNotFoundException;
import com.alertamujer.backend.shared.errors.StateConflictException;
import com.alertamujer.backend.shared.errors.UnauthorizedException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.security.IdentityAuthentication;
import com.alertamujer.backend.shared.security.JwtAccessTokenService;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/** Applies the REST token and emergency authorization rules to every relevant STOMP frame. */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {
    private static final Pattern TOPIC = Pattern.compile("^/topic/emergencies/([0-9a-fA-F-]{36})$");
    private static final Pattern SEND = Pattern.compile("^/app/emergencies/([0-9a-fA-F-]{36})/messages$");

    private final JwtAccessTokenService jwt;
    private final AuthenticationService authenticationService;
    private final ChatService chatService;

    public StompAuthenticationInterceptor(JwtAccessTokenService jwt, AuthenticationService authenticationService,
            ChatService chatService) {
        this.jwt = jwt;
        this.authenticationService = authenticationService;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) return message;
        if (StompCommand.CONNECT.equals(command) || StompCommand.STOMP.equals(command)) {
            AuthenticatedIdentity identity = authenticate(accessor.getFirstNativeHeader("Authorization"));
            accessor.setUser(new IdentityAuthentication(identity));
            return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            AuthenticatedIdentity identity = identity(accessor);
            chatService.authorizeSubscription(identity, destination(accessor.getDestination(), TOPIC));
        } else if (StompCommand.SEND.equals(command)) {
            AuthenticatedIdentity identity = identity(accessor);
            UUID emergencyId = destination(accessor.getDestination(), SEND);
            chatService.authorizeSubscription(identity, emergencyId);
        }
        return message;
    }

    private AuthenticatedIdentity authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == "Bearer ".length()) {
            throw new UnauthorizedException();
        }
        try {
            Jwt token = jwt.decode(authorization.substring("Bearer ".length()));
            UUID userId = UUID.fromString(token.getSubject());
            UUID sessionId = UUID.fromString(token.getClaimAsString("sid"));
            String role = token.getClaimAsString("role");
            if (!"USER".equals(role) && !"ENTITY_ADMIN".equals(role)) throw new IllegalArgumentException("invalid role");
            AuthenticatedIdentity identity = authenticationService.validateAccessSession(userId, sessionId, role);
            if (identity.termsPending()) throw new ForbiddenException();
            return identity;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException();
        }
    }

    private AuthenticatedIdentity identity(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof IdentityAuthentication authentication)) throw new UnauthorizedException();
        return (AuthenticatedIdentity) authentication.getPrincipal();
    }

    private UUID destination(String value, Pattern pattern) {
        Matcher matcher = value == null ? null : pattern.matcher(value);
        if (matcher == null || !matcher.matches()) throw new ForbiddenException();
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException();
        }
    }
}
