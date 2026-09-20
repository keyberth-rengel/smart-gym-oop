package com.smartgym.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Customer summary (no payment or booking-history data)")
public record CustomerSummary(String email, String name, int age) {}
