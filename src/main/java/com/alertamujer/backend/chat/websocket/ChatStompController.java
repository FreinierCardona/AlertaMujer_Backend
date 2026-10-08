package com.alertamujer.backend.chat.websocket;

import com.alertamujer.backend.chat.dto.request.ChatMessageInput;
import com.alertamujer.backend.chat.service.ChatService;
import com.alertamujer.backend.shared.errors.UnauthorizedException;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import com.alertamujer.backend.shared.security.IdentityAuthentication;
import java.security.Principal;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/** Thin STOMP endpoint: chat service persists first and its after-commit listener publishes. */
@Controller
public class ChatStompController {
    private final ChatService service;

    public ChatStompController(ChatService service) { this.service = service; }

    @MessageMapping("/emergencies/{emergencyId}/messages")
    public void send(@DestinationVariable UUID emergencyId, @Payload ChatMessageInput input, Principal principal) {
        service.send(identity(principal), emergencyId, input);
    }

    private AuthenticatedIdentity identity(Principal principal) {
        if (!(principal instanceof IdentityAuthentication authentication)) throw new UnauthorizedException();
        return (AuthenticatedIdentity) authentication.getPrincipal();
    }
}
