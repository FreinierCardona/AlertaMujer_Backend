package com.alertamujer.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigurationCorsTest {

    @Test
    void limitsCorsToConfiguredOriginsAndFrontendHeaders() {
        SecurityConfiguration security = new SecurityConfiguration();
        CorsConfigurationSource source = security.corsConfigurationSource(
                "http://localhost:5173, http://127.0.0.1:4173");

        CorsConfiguration configuration = source.getCorsConfiguration(
                new MockHttpServletRequest("OPTIONS", "/api/v1/registration-requests"));

        assertEquals(List.of("http://localhost:5173", "http://127.0.0.1:4173"),
                configuration.getAllowedOrigins());
        assertEquals(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                configuration.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type", "X-Request-Id"),
                configuration.getAllowedHeaders());
        assertEquals(List.of("X-Request-Id"), configuration.getExposedHeaders());
    }
}
