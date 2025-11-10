package com.smartgym.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Progress item")
public record ProgressItemResponse(
        java.time.LocalDate date,
        double weightKg,
        double bodyFatPct,
        double musclePct
) {}