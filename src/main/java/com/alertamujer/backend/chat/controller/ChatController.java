package com.alertamujer.backend.chat.controller;

import com.alertamujer.backend.chat.dto.response.ChatMessageResponse;
import com.alertamujer.backend.chat.service.ChatService;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST recovery endpoint; STOMP is the only write transport for emergency chat. */
@RestController
@Validated
@RequestMapping("/api/v1/emergencies/{emergencyId}/messages")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) { this.service = service; }

    @GetMapping
    public List<ChatMessageResponse> list(@AuthenticationPrincipal AuthenticatedIdentity identity, @PathVariable UUID emergencyId,
            @RequestParam(defaultValue = "0") @Min(0) long after,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return service.list(identity, emergencyId, after, size);
    }
}
