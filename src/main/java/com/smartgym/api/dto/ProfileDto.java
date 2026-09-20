package com.smartgym.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Perfil de un cliente o entrenador. {@code specialty} solo aplica a entrenadores. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileDto(String email, String name, int age, String specialty) {}
