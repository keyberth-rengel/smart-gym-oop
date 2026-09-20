package com.smartgym.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Acceso al usuario autenticado (claims "role" y "email" del session token de Clerk). */
@Component
public class CurrentUser {

    public static final String EMAIL_CLAIM = "email";

    public Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new AuthenticationCredentialsNotFoundException("Authentication required");
    }

    public Role role() {
        return Role.fromClaim(jwt().getClaims().get(JwtRoleConverter.ROLE_CLAIM));
    }

    /** Correo normalizado (minúsculas, sin espacios). 403 si el token no lo incluye. */
    public String email() {
        Object claim = jwt().getClaims().get(EMAIL_CLAIM);
        if (claim == null || claim.toString().isBlank()) {
            throw new ForbiddenException(
                    "The token does not include the email; configure the session token in Clerk");
        }
        return claim.toString().trim().toLowerCase(Locale.ROOT);
    }

    public boolean isAdmin() { return role() == Role.ADMIN; }

    public boolean isTrainer() { return role() == Role.ENTRENADOR; }

    public boolean isCustomer() { return role() == Role.CLIENTE; }
}
