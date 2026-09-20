package com.smartgym.security;

import com.smartgym.repository.BookingRepository;
import com.smartgym.repository.IdentityLinkRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Reglas de acceso reutilizables por los controladores. Todas lanzan {@link ForbiddenException} (HTTP 403)
 * y se evalúan ANTES de mirar si el recurso existe: para un usuario sin permiso, un recurso ajeno y uno
 * inexistente responden igual y no se filtra qué datos existen.
 *
 * <p>Vocabulario: "dueño" = el correo del recurso es el del usuario autenticado; "cliente del entrenador" =
 * existe al menos una reserva entre ambos. Los correos se comparan sin distinguir mayúsculas.
 */
@Component
public class AccessGuard {

    private static final String DENIED = "Access denied for this resource";

    private final CurrentUser user;
    private final IdentityLinkRepository links;
    private final BookingRepository bookings;

    public AccessGuard(CurrentUser user, IdentityLinkRepository links, BookingRepository bookings) {
        this.user = user;
        this.links = links;
        this.bookings = bookings;
    }

    /**
     * Datos de un entrenador: el admin accede a cualquiera, un entrenador solo a los suyos
     * (correo sin distinguir mayúsculas) y un cliente a ninguno. Si no, 403.
     */
    public void requireTrainerScope(String trainerEmail) {
        if (user.isAdmin()) return;
        if (user.isTrainer() && trainerEmail != null && user.email().equals(normalize(trainerEmail))) {
            return;
        }
        throw new ForbiddenException("Access denied for this trainer");
    }

    /** Solo administración. */
    public void requireAdmin() {
        if (!user.isAdmin()) throw new ForbiddenException("Administrator role required");
    }

    /** Datos de un cliente: admin, el propio cliente (dueño) o un entrenador que tenga reservas con él. */
    public void requireCustomerDataByEmail(String customerEmail) {
        if (user.isAdmin()) return;
        if (isSelf(customerEmail) || isTrainerOf(customerEmail)) return;
        throw new ForbiddenException(DENIED);
    }

    /** Igual que {@link #requireCustomerDataByEmail} a partir del DNI. DNI sin vínculo => 403 (no admin). */
    public void requireCustomerDataByDni(String dni) {
        if (user.isAdmin()) return;
        String email = emailOfDni(dni).orElseThrow(() -> new ForbiddenException(DENIED));
        requireCustomerDataByEmail(email);
    }

    /** El DNI es del propio usuario (cualquier rol) o es administración. */
    public void requireOwnDniOrAdmin(String dni) {
        if (user.isAdmin()) return;
        String email = emailOfDni(dni).orElseThrow(() -> new ForbiddenException(DENIED));
        if (!isSelf(email)) throw new ForbiddenException(DENIED);
    }

    /** Escribir datos de un cliente por DNI (progreso): admin cualquiera; cliente solo el suyo; entrenador nunca. */
    public void requireCustomerWriteByDni(String dni) {
        if (user.isAdmin()) return;
        if (user.isTrainer()) throw new ForbiddenException(DENIED);
        requireOwnDniOrAdmin(dni);
    }

    /** Asignar rutina por correo: admin cualquiera; entrenador solo a sus clientes; cliente nunca. */
    public void requireRoutineAssignByEmail(String customerEmail) {
        if (user.isAdmin()) return;
        if (user.isTrainer() && isTrainerOf(customerEmail)) return;
        throw new ForbiddenException(DENIED);
    }

    /** Asignar rutina por DNI: mismas reglas que por correo; DNI sin vínculo => 403 (no admin). */
    public void requireRoutineAssignByDni(String dni) {
        if (user.isAdmin()) return;
        if (!user.isTrainer()) throw new ForbiddenException(DENIED);
        String email = emailOfDni(dni).orElseThrow(() -> new ForbiddenException(DENIED));
        requireRoutineAssignByEmail(email);
    }

    /** Crear una reserva a nombre de un cliente: admin cualquiera; cliente solo la suya; entrenador nunca. */
    public void requireBookingFor(String customerEmail) {
        if (user.isAdmin()) return;
        if (user.isCustomer() && isSelf(customerEmail)) return;
        throw new ForbiddenException(DENIED);
    }

    private boolean isSelf(String email) {
        return email != null && user.email().equals(normalize(email));
    }

    private boolean isTrainerOf(String customerEmail) {
        return user.isTrainer() && customerEmail != null
                && bookings.existsByTrainer_EmailAndCustomer_Email(user.email(), normalize(customerEmail));
    }

    private Optional<String> emailOfDni(String dni) {
        if (dni == null || dni.isBlank()) return Optional.empty();
        return links.findById(normalize(dni)).map(l -> l.getEmail());
    }

    private static String normalize(String s) { return s.trim().toLowerCase(Locale.ROOT); }
}
