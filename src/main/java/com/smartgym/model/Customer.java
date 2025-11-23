package com.smartgym.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.*;
import com.smartgym.domain.PaymentMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @Column(nullable = false, length = 180)
    private String email;

    @Column(nullable = false, length = 120)
    private String name;

    private int age;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "customer_booking_history", joinColumns = @JoinColumn(name = "customer_email"))
    @Column(name = "note", length = 255)
    private List<String> bookingHistory = new ArrayList<>();

    @Embedded
    private PaymentMethod paymentMethod;

    protected Customer() {
        // Constructor JPA
    }

    public Customer(String email, String name, int age) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email required");
        }
        this.email = normalize(email);
        this.name = name;
        this.age = age;
    }

    private String normalize(String e) { return e.toLowerCase().trim(); }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public int getAge() { return age; }

    public void setName(String name) { this.name = name; }
    public void setAge(int age) { this.age = age; }

    public void addHistory(String note) { bookingHistory.add(note); }
    public List<String> getBookingHistory() { return Collections.unmodifiableList(bookingHistory); }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    @Override
    public String toString() {
        return "Customer{" +
                "email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}