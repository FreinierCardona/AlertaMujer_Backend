package com.alertamujer.backend.shared.security;

import com.alertamujer.backend.identity.service.AuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Valida el JWT y consulta la sesión persistida para detectar revocaciones en cada solicitud autenticada. */
@Component
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtAccessTokenService jwt;
    private final AuthenticationService authenticationService;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public AccessTokenAuthenticationFilter(JwtAccessTokenService jwt, AuthenticationService authenticationService,
            SecurityErrorResponseWriter errorResponseWriter) {
        this.jwt = jwt;
        this.authenticationService = authenticationService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() == "Bearer ".length()) {
            unauthorized(request, response);
            return;
        }
        try {
            Jwt token = jwt.decode(authorization.substring("Bearer ".length()));
            UUID userId = UUID.fromString(token.getSubject());
            UUID sessionId = UUID.fromString(token.getClaimAsString("sid"));
            String role = token.getClaimAsString("role");
            if (role == null || (!"USER".equals(role) && !"ENTITY_ADMIN".equals(role))) {
                throw new IllegalArgumentException("invalid role");
            }
            // El cierre de sesión usa una validación específica para admitir cierres repetidos.
            AuthenticatedIdentity identity = isLogoutRequest(request)
                    ? authenticationService.validateLogoutSession(userId, sessionId, role)
                    : authenticationService.validateAccessSession(userId, sessionId, role);
            // Mientras falte aceptar términos, solo se permiten las rutas de continuidad de sesión.
            if (identity.termsPending() && !isAllowedWhileTermsPending(request)) {
                errorResponseWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "Access is denied.");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(new IdentityAuthentication(identity));
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            unauthorized(request, response);
        // Limpia la identidad incluso si el procesamiento falla, pues el hilo puede atender otra solicitud.
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isAllowedWhileTermsPending(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return isLogoutRequest(request)
                || "/api/v1/auth/refresh".equals(uri)
                || "/api/v1/me/terms-acceptance".equals(uri);
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/api/v1/auth/logout".equals(request.getRequestURI());
    }

    private void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        errorResponseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication is required.");
    }
}
