package com.alertamujer.backend.shared.security;

import com.alertamujer.backend.shared.errors.ApiError;
import com.alertamujer.backend.shared.observability.RequestIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the common API error when Spring Security rejects a request before it
 * reaches a controller advice.
 */
@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(code, message, RequestIdContext.getRequestId(request)));
    }
}
