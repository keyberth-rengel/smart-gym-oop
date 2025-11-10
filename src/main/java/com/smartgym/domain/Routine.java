package com.smartgym.domain;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

public class Routine {
    private final Map<DayOfWeek, String> plan = new EnumMap<>(DayOfWeek.class);
    private final LocalDateTime createdAt = LocalDateTime.now();

    public Routine(Map<DayOfWeek, String> planByDay) {
        if (planByDay != null) {
            plan.putAll(planByDay);
        }
    }

    public String getFor(DayOfWeek day) {
        return plan.get(day);
    }

    public Map<DayOfWeek, String> getPlan() {
        return plan;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Routine{createdAt=" + createdAt + ", plan=" + plan + "}";
    }
}