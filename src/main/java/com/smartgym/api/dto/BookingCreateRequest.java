package com.smartgym.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Create booking request")
public record BookingCreateRequest(
        @NotBlank @JsonAlias({"customer_email","customerEmail"}) String customerEmail,
        @NotBlank @JsonAlias({"trainer_email","trainerEmail"}) String trainerEmail,
        @NotBlank @JsonAlias({"date"}) String date,
        @NotBlank @JsonAlias({"time"}) String time,
        @jakarta.validation.constraints.Size(max = 250) String note
) {}