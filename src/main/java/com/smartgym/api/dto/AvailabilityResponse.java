package com.smartgym.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Occupied times of a trainer on a date (no personal data)")
public record AvailabilityResponse(
        @Schema(example = "2026-09-19") String date,
        @Schema(description = "Booked times (HH:mm), ascending") List<String> bookedTimes
) {}
