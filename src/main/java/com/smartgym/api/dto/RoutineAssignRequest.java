package com.smartgym.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RoutineAssignRequest(@NotBlank String dni) {}