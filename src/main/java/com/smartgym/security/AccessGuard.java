package com.smartgym.security;

import org.springframework.stereotype.Component;

/** Reglas de acceso reutilizables por los controladores. */
@Component
public class AccessGuard {

    private final CurrentUser user;

    public AccessGuard(CurrentUser user) { this.user = user; }

    /**
     * Datos de un entrenador: el admin accede a cualquiera, un entrenador solo a los suyos
     * (correo sin distinguir mayúsculas) y un cliente a ninguno. Si no, 403.
     */
    public void requireTrainerScope(String trainerEmail) {
        if (user.isAdmin()) return;
        if (user.isTrainer() && trainerEmail != null
                && user.email().equals(trainerEmail.trim().toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        throw new ForbiddenException("Access denied for this trainer");
    }
}
