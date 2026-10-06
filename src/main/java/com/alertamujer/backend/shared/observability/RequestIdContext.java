package com.alertamujer.backend.shared.observability;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Gives the shared HTTP components access to the correlation identifier of a request.
 */
public final class RequestIdContext {

    public static final String ATTRIBUTE_NAME = RequestIdContext.class.getName() + ".requestId";

    private RequestIdContext() {
    }

    public static UUID getRequestId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE_NAME);
        return value instanceof UUID requestId ? requestId : UUID.randomUUID();
    }
}
