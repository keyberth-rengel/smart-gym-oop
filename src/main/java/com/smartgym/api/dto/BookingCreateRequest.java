package com.smartgym.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Create booking request")
public record BookingCreateRequest(
        @NotBlank @Email(message = "Invalid customer email")
        @JsonAlias({"customer_email","customerEmail"}) String customerEmail,
        @NotBlank @Email(message = "Invalid trainer email")
        @JsonAlias({"trainer_email","trainerEmail"}) String trainerEmail,
        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Time must be HH:mm 24h")
        @JsonAlias({"time"}) String time,
        @jakarta.validation.constraints.Size(max = 250) String note
) {}