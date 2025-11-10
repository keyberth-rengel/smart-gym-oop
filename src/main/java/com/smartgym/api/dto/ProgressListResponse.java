package com.smartgym.api.dto;

import java.util.List;

public record ProgressListResponse(
        List<ProgressItemResponse> items, int total,
        double avgWeightKg, double avgBodyFatPct, double avgMusclePct
) {}