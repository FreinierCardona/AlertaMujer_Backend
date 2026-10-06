package com.alertamujer.backend.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds a safe correlation identifier to every HTTP request and response.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        UUID requestId = parseRequestId(request.getHeader(HEADER_NAME));
        request.setAttribute(RequestIdContext.ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId.toString());

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, requestId.toString())) {
            filterChain.doFilter(request, response);
        }
    }

    private UUID parseRequestId(String headerValue) {
        if (headerValue == null) {
            return UUID.randomUUID();
        }

        try {
            UUID candidate = UUID.fromString(headerValue);
            return candidate.toString().equalsIgnoreCase(headerValue) ? candidate : UUID.randomUUID();
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID();
        }
    }
}
