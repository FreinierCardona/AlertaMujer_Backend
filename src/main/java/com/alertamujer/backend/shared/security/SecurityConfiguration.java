package com.alertamujer.backend.shared.security;

import com.alertamujer.backend.shared.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Define únicamente el contrato transversal de respuestas de seguridad. Las reglas de autenticación y
 * autorización se agregan en sus correspondientes HUs de identidad.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestIdFilter requestIdFilter,
            AccessTokenAuthenticationFilter accessTokenAuthenticationFilter,
            SecurityErrorResponseWriter errorResponseWriter,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/registration-requests").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/registration-requests/*/verification-codes").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/registration-requests/*/verification-codes/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/registration-requests").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v2/registration-requests/*/verification-codes").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v2/registration-requests/*/verification-codes/verify").permitAll()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v2/registration-requests/*/credentials").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/password-reset/verification-codes").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/password-reset/confirm").permitAll()
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ENTITY_ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHENTICATED", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponseWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN", "Access is denied.")))
                .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(accessTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Permite solo los orígenes de navegador configurados. Los clientes móviles nativos no envían la
     * cabecera Origin; la aplicación web usa un token Bearer en lugar de cookies entre sitios, por lo que
     * no se habilitan credenciales.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173}")
            String configuredOrigins) {
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain at least one origin");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Request-Id", "X-Registration-Proof"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Calcula el hash de las credenciales antes de que lleguen a persistencia. La contraseña sin procesar
     * se acepta solo en los DTO de solicitud de registro.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
