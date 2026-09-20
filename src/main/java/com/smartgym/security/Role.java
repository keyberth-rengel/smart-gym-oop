package com.smartgym.security;

import java.util.Locale;

/**
 * Roles de la aplicación. Vienen del claim "role" del session token de Clerk
 * ("cliente" | "entrenador" | "admin"). Cualquier valor ausente o desconocido se trata como CLIENTE
 * para no otorgar privilegios por error.
 */
public enum Role {
    CLIENTE, ENTRENADOR, ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role fromClaim(Object claim) {
        if (claim == null) return CLIENTE;
        String v = claim.toString().trim().toUpperCase(Locale.ROOT);
        for (Role r : values()) {
            if (r.name().equals(v)) return r;
        }
        return CLIENTE;
    }
}
