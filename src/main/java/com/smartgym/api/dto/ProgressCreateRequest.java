package com.smartgym.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

// POJO estándar en lugar de record para evitar posibles problemas de nombres en Jackson.
public class ProgressCreateRequest {

        @NotBlank
        @JsonProperty("dni")
        private String dni;

        @NotNull
        @DecimalMin(value = "0.1", message = "Weight must be > 0")
        @DecimalMax(value = "400", message = "Weight must be <= 400")
        @JsonProperty("weightKg")
        private Double weightKg;

        @NotNull
        @DecimalMin(value = "0", message = "Body fat must be >= 0")
        @DecimalMax(value = "100", message = "Body fat must be <= 100")
        @JsonProperty("bodyFatPct")
        private Double bodyFatPct;

        @NotNull
        @DecimalMin(value = "0", message = "Muscle % must be >= 0")
        @DecimalMax(value = "100", message = "Muscle % must be <= 100")
        @JsonProperty("musclePct")
        private Double musclePct;

        @io.swagger.v3.oas.annotations.media.Schema(description = "Optional client-local calendar date (yyyy-MM-dd). Must be within one day of the server's UTC date.",
                example = "2026-10-08", nullable = true)
        @jakarta.validation.constraints.Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be yyyy-MM-dd")
        @JsonProperty("date")
        private String date;

        @io.swagger.v3.oas.annotations.media.Schema(description = "Optional client UTC offset in minutes (e.g. Lima = -300). With `date`, enforces the client's local today.",
                example = "-300", nullable = true)
        @jakarta.validation.constraints.Min(value = -720, message = "utcOffsetMinutes must be between -720 and 840")
        @jakarta.validation.constraints.Max(value = 840, message = "utcOffsetMinutes must be between -720 and 840")
        @JsonProperty("utcOffsetMinutes")
        @JsonAlias({"utc_offset_minutes"})
        private Integer utcOffsetMinutes;

        public ProgressCreateRequest() {}

        public ProgressCreateRequest(String dni, Double weightKg, Double bodyFatPct, Double musclePct) {
                this.dni = dni;
                this.weightKg = weightKg;
                this.bodyFatPct = bodyFatPct;
                this.musclePct = musclePct;
        }

        public String getDni() { return dni; }
        public Double getWeightKg() { return weightKg; }
        public Double getBodyFatPct() { return bodyFatPct; }
        public Double getMusclePct() { return musclePct; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public Integer getUtcOffsetMinutes() { return utcOffsetMinutes; }
        public void setUtcOffsetMinutes(Integer v) { this.utcOffsetMinutes = v; }
        public void setDni(String dni) { this.dni = dni; }
        public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
        public void setBodyFatPct(Double bodyFatPct) { this.bodyFatPct = bodyFatPct; }
        public void setMusclePct(Double musclePct) { this.musclePct = musclePct; }
}