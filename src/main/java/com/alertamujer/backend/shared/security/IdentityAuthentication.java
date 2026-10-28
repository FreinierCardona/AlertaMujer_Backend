package com.alertamujer.backend.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Token de SecurityContext cuyos permisos provienen de la cuenta actual, nunca del cuerpo de una solicitud. */
public final class IdentityAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedIdentity identity;

    public IdentityAuthentication(AuthenticatedIdentity identity) {
        super(java.util.List.of(new SimpleGrantedAuthority("ROLE_" + identity.role())));
        this.identity = identity;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public AuthenticatedIdentity getPrincipal() {
        return identity;
    }
}
