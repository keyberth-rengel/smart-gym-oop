package com.smartgym.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Completar el perfil de un cliente autenticado (el correo sale del token)")
public record OnboardingRequest(
        @NotBlank @Size(max = 120)
        @Pattern(regexp = "^[^<>]*$", message = "Name cannot contain angle brackets") String name,
        @NotNull @Min(0) Integer age,
        @NotBlank
        @Pattern(regexp = "^\\d{8}$", message = "DNI must be 8 digits") String dni
) {}
