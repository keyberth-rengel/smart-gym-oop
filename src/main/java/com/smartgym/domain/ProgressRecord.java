package com.smartgym.domain;

import com.smartgym.model.Customer;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "progress_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_progress_customer_date", columnNames = {"customer_email", "date"})
})
public class ProgressRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_email", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private LocalDate date;

    private double weightKg;
    private double bodyFatPct;
    private double musclePct;

    protected ProgressRecord() { }

    public ProgressRecord(Customer customer, LocalDate date, double weightKg, double bodyFatPct, double musclePct) {
        this.customer = customer;
        this.date = date;
        this.weightKg = weightKg;
        this.bodyFatPct = bodyFatPct;
        this.musclePct = musclePct;
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public LocalDate getDate() { return date; }
    public double getWeightKg() { return weightKg; }
    public double getBodyFatPct() { return bodyFatPct; }
    public double getMusclePct() { return musclePct; }
}