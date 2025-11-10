package com.smartgym.domain;

import java.time.LocalDate;

public class ProgressRecord {
    private final java.time.LocalDate date;
    private final double weightKg;
    private final double bodyFatPct;
    private final double musclePct;

    public ProgressRecord(java.time.LocalDate date, double weightKg, double bodyFatPct, double musclePct) {
        this.date = date;
        this.weightKg = weightKg;
        this.bodyFatPct = bodyFatPct;
        this.musclePct = musclePct;
    }
    public java.time.LocalDate getDate() { return date; }
    public double getWeightKg() { return weightKg; }
    public double getBodyFatPct() { return bodyFatPct; }
    public double getMusclePct() { return musclePct; }
}