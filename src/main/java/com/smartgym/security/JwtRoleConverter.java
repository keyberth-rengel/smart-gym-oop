package com.smartgym.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;

/** Convierte el claim "role" del JWT en una autoridad ROLE_CLIENTE / ROLE_ENTRENADOR / ROLE_ADMIN. */
public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String ROLE_CLAIM = "role";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Role role = Role.fromClaim(jwt.getClaims().get(ROLE_CLAIM));
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
}
