package com.smartgym.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * JwtDecoder de pruebas para ejercitar la cadena real de seguridad (Bearer, conversor de roles, entry points)
 * sin firmar tokens. Formato del token: "t.<role>.<email en base64url>" (role "none" = sin claim, email vacío = sin claim).
 * Solo usa caracteres válidos para un Bearer (RFC 6750). Cualquier otro valor es un token inválido.
 */
@TestConfiguration
public class TestJwtDecoderConfig {

    public static String token(String role, String email) {
        String e = email == null ? "" : Base64.getUrlEncoder().withoutPadding()
                .encodeToString(email.getBytes(StandardCharsets.UTF_8));
        return "t." + (role == null ? "none" : role) + "." + e;
    }

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
        return raw -> {
            String[] p = raw.split("\\.", -1);
            if (p.length != 3 || !p[0].equals("t")) throw new BadJwtException("invalid token");
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", "user_test");
            if (!p[1].equals("none")) claims.put("role", p[1]);
            if (!p[2].isEmpty()) claims.put("email", new String(Base64.getUrlDecoder().decode(p[2]), StandardCharsets.UTF_8));
            return new Jwt(raw, Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
        };
    }
}
