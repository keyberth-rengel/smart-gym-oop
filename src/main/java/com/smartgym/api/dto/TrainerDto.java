package com.smartgym.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record TrainerDto(
        @Email @NotBlank String email,
        @jakarta.validation.constraints.Size(max = 120) String name,
        @Min(0) int age,
        @jakarta.validation.constraints.Size(max = 80)  String specialty
) {}