package com.alertamujer.backend.shared.security;

import com.alertamujer.backend.shared.observability.RequestIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Defines only the cross-cutting security response contract. Authentication and
 * authorization rules are added by their corresponding identity HUs.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestIdFilter requestIdFilter,
            SecurityErrorResponseWriter errorResponseWriter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHENTICATED", "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponseWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN", "Access is denied.")))
                .addFilterBefore(requestIdFilter, SecurityContextHolderFilter.class)
                .build();
    }
}
