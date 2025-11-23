package com.smartgym.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerDto(
        @Email @NotBlank String email,
        @NotBlank @jakarta.validation.constraints.Size(max = 120)
        @Pattern(regexp = "^[^<>]*$", message = "Name cannot contain angle brackets") String name,
        @Min(0) int age
) {}