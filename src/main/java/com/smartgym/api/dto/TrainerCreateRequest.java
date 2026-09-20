package com.smartgym.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Alta de entrenador (solo admin). {@code dni} es opcional; si viene, se vincula al correo del entrenador. */
public record TrainerCreateRequest(
        @Email @NotBlank String email,
        @NotBlank @jakarta.validation.constraints.Size(max = 120)
        @Pattern(regexp = "^[^<>]*$", message = "Name cannot contain angle brackets") String name,
        @Min(0) int age,
        @jakarta.validation.constraints.Size(max = 80) String specialty,
        @Pattern(regexp = "^\\d{8}$", message = "DNI must be 8 digits") String dni
) {}
