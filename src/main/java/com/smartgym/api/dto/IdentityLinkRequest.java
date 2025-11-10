package com.smartgym.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record IdentityLinkRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{8}$", message = "DNI must be 8 digits")
        String dni,
        @NotBlank
        @Email
        String email
) {}