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
        @jakarta.validation.constraints.Size(max = 250) String note,
        @Schema(description = "Optional client-local calendar date (yyyy-MM-dd). Must be within one day of the server's UTC date.",
                example = "2026-10-08", nullable = true)
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be yyyy-MM-dd") String date,
        @Schema(description = "Optional client UTC offset in minutes (e.g. Lima = -300). With `date`, enforces the client's local today and a strict past check.",
                example = "-300", nullable = true)
        @jakarta.validation.constraints.Min(value = -720, message = "utcOffsetMinutes must be between -720 and 840")
        @jakarta.validation.constraints.Max(value = 840, message = "utcOffsetMinutes must be between -720 and 840")
        @JsonAlias({"utcOffsetMinutes", "utc_offset_minutes"}) Integer utcOffsetMinutes
) {}
