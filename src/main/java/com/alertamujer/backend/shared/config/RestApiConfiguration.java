package com.alertamujer.backend.shared.config;

import org.springframework.context.annotation.Configuration;
/**
 * Shared MVC configuration.
 *
 * <p>Controllers declare their full versioned contract paths themselves. Adding a
 * second global prefix here would expose them under {@code /api/v1/api/v1/...}.
 */
@Configuration
public class RestApiConfiguration { }
