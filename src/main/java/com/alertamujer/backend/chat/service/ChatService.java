package com.alertamujer.backend.chat.service;

import com.alertamujer.backend.chat.dto.request.ChatMessageInput;
import com.alertamujer.backend.chat.dto.response.ChatMessageResponse;
import com.alertamujer.backend.shared.security.AuthenticatedIdentity;
import java.util.List;
import java.util.UUID;

/** Authorizes and persists the chat part of one emergency. */
public interface ChatService {
    List<ChatMessageResponse> list(AuthenticatedIdentity identity, UUID emergencyId, long after, int size);
    ChatMessageResponse send(AuthenticatedIdentity identity, UUID emergencyId, ChatMessageInput input);
    void authorizeSubscription(AuthenticatedIdentity identity, UUID emergencyId);
}
