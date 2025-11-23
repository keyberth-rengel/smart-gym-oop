package com.smartgym.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records", indexes = {
        @Index(name = "idx_attendance_email_time", columnList = "email,timestamp")
})
public class AttendanceRecord {
    public enum Role { CUSTOMER, TRAINER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    protected AttendanceRecord() { }

    public AttendanceRecord(String email, Role role) {
        this.email = normalize(email);
        this.role = role;
        this.timestamp = LocalDateTime.now();
    }

    private String normalize(String s) { return s == null ? null : s.trim().toLowerCase(); }

    @PrePersist
    public void prePersist() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "Attendance{" + role + ", email='" + email + "', at=" + timestamp + "}";
    }
}