package com.smartgym.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUnitTest {

    private static final String ISSUER = "https://brave-sawfish-4330.clerk.accounts.dev";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt(Map<String, Object> extra, String issuer, Instant exp) {
        Map<String, Object> claims = new HashMap<>(extra);
        claims.put("sub", "user_1");
        if (issuer != null) claims.put("iss", issuer);
        Instant iat = exp.isBefore(Instant.now()) ? exp.minusSeconds(60) : Instant.now().minusSeconds(30);
        return new Jwt("tok", iat, exp, Map.of("alg", "RS256"), claims);
    }

    private static void login(Map<String, Object> claims) {
        Jwt j = jwt(claims, ISSUER, Instant.now().plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(j));
    }

    @Test
    void roleFromClaimMapsKnownValuesCaseInsensitively() {
        assertEquals(Role.ADMIN, Role.fromClaim("admin"));
        assertEquals(Role.ADMIN, Role.fromClaim(" ADMIN "));
        assertEquals(Role.ENTRENADOR, Role.fromClaim("Entrenador"));
        assertEquals(Role.CLIENTE, Role.fromClaim("cliente"));
    }

    @Test
    void missingOrUnknownRoleFallsBackToCliente() {
        assertEquals(Role.CLIENTE, Role.fromClaim(null));
        assertEquals(Role.CLIENTE, Role.fromClaim(""));
        assertEquals(Role.CLIENTE, Role.fromClaim("superuser"));
        assertEquals(Role.CLIENTE, Role.fromClaim(42));
    }

    @Test
    void converterProducesSingleRoleAuthority() {
        var jwt = jwt(Map.of("role", "entrenador"), ISSUER, Instant.now().plusSeconds(60));
        var auths = new JwtRoleConverter().convert(jwt);
        assertEquals(1, auths.size());
        assertEquals("ROLE_ENTRENADOR", auths.iterator().next().getAuthority());
        var none = new JwtRoleConverter().convert(jwt(Map.of(), ISSUER, Instant.now().plusSeconds(60)));
        assertEquals("ROLE_CLIENTE", none.iterator().next().getAuthority());
    }

    @Test
    void validatorAcceptsValidTokenAndRejectsWrongIssuerAndExpired() {
        var validator = SecurityConfig.jwtValidator(ISSUER);
        assertFalse(validator.validate(jwt(Map.of(), ISSUER, Instant.now().plusSeconds(60))).hasErrors());
        assertTrue(validator.validate(jwt(Map.of(), "https://evil.example", Instant.now().plusSeconds(60))).hasErrors());
        assertTrue(validator.validate(jwt(Map.of(), null, Instant.now().plusSeconds(60))).hasErrors());
        OAuth2TokenValidatorResult expired = validator.validate(jwt(Map.of(), ISSUER, Instant.now().minusSeconds(600)));
        assertTrue(expired.hasErrors());
    }

    @Test
    void currentUserReadsRoleAndNormalizesEmail() {
        login(Map.of("role", "admin", "email", "  Admin@Example.COM "));
        var user = new CurrentUser();
        assertEquals(Role.ADMIN, user.role());
        assertTrue(user.isAdmin());
        assertFalse(user.isCustomer());
        assertEquals("admin@example.com", user.email());
    }

    @Test
    void currentUserWithoutRoleIsCustomer() {
        login(Map.of("email", "a@example.com"));
        var user = new CurrentUser();
        assertTrue(user.isCustomer());
        assertFalse(user.isTrainer());
    }

    @Test
    void currentUserWithoutEmailIsForbiddenWithClearMessage() {
        login(Map.of("role", "cliente"));
        var ex = assertThrows(ForbiddenException.class, () -> new CurrentUser().email());
        assertTrue(ex.getMessage().contains("configure the session token in Clerk"));
        login(Map.of("role", "cliente", "email", "  "));
        assertThrows(ForbiddenException.class, () -> new CurrentUser().email());
    }

    @Test
    void currentUserWithoutAuthenticationThrows() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> new CurrentUser().role());
    }
}
