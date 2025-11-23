package com.smartgym.model;

import jakarta.persistence.*;

@Entity
@Table(name = "trainers")
public class Trainer {
    @Id
    @Column(nullable = false, length = 180)
    private String email;

    @Column(nullable = false, length = 120)
    private String name;

    private int age;

    @Column(length = 160)
    private String specialty;

    protected Trainer() {
        // JPA
    }

    public Trainer(String email, String name, int age, String specialty) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        this.email = normalize(email);
        this.name = name;
        this.age = age;
        this.specialty = specialty;
    }

    private String normalize(String e) { return e.toLowerCase().trim(); }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getSpecialty() { return specialty; }

    public void setName(String name) { this.name = name; }
    public void setAge(int age) { this.age = age; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    @Override
    public String toString() {
        return "Trainer{" +
                "email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", age=" + age +
                ", specialty='" + specialty + '\'' +
                '}';
    }
}