package com.smartgym.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProgressCreateRequest(
        @NotBlank String dni,
        @Positive double weightKg,
        @Positive double bodyFatPct,
        @Positive double musclePct
) {}