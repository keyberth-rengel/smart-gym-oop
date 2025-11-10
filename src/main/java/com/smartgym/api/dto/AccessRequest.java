package com.smartgym.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AccessRequest(@NotBlank String dni) {}