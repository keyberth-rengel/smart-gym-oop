package com.smartgym.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;

/**
 * Asignar rutina semanal: se envía EXACTAMENTE uno de {@code dni} o {@code customer_email}
 * (el entrenador conoce correos, recepción conoce DNI). Ninguno o ambos => 400.
 */
@ExactlyOneIdentifier
public record RoutineAssignRequest(
        String dni,
        @Email(message = "Invalid customer email")
        @JsonAlias({"customer_email", "customerEmail"}) String customerEmail
) {}
