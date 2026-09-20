package com.smartgym.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Customer that has at least one booking with the trainer")
public record TrainerCustomerItem(
        String email,
        String name,
        int age,
        @Schema(description = "Number of bookings with this trainer") long sessions,
        @Schema(description = "Date of the most recent booking (yyyy-MM-dd)", example = "2026-09-19") String lastBookingDate,
        @Schema(description = "Time of the most recent booking (HH:mm)", example = "16:30") String lastBookingTime
) {}
