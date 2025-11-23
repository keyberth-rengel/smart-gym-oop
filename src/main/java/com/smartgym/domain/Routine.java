package com.smartgym.domain;

import com.smartgym.model.Customer;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Entity
@Table(name = "routines", indexes = {
        @Index(name = "idx_routine_customer_created", columnList = "customer_email,created_at")
})
public class Routine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_email", nullable = false)
    private Customer customer;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "routine_plan", joinColumns = @JoinColumn(name = "routine_id"))
    @MapKeyEnumerated(EnumType.STRING)
    // Usar varchar en vez de enum específico para compatibilidad H2
    @MapKeyColumn(name = "weekday", length = 16, columnDefinition = "varchar(16)")
    @Column(name = "block", length = 64)
    private Map<DayOfWeek, String> plan = new EnumMap<>(DayOfWeek.class);

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Routine() { }

    public Routine(Customer customer, Map<DayOfWeek, String> planByDay) {
        this.customer = customer;
        if (planByDay != null) plan.putAll(planByDay);
    }

    public Long getId() { return id; }
    public Customer getCustomer() { return customer; }
    public String getFor(DayOfWeek day) { return plan.get(day); }
    public Map<DayOfWeek, String> getPlan() { return plan; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}