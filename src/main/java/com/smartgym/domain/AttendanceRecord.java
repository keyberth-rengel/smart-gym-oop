package com.smartgym.domain;

import java.time.LocalDateTime;

public class AttendanceRecord {
    public enum Role { CUSTOMER, TRAINER }

    private final String email;
    private final Role role;
    private final LocalDateTime timestamp;

    public AttendanceRecord(String email, Role role, LocalDateTime now) {
        this.email = email;
        this.role = role;
        this.timestamp = LocalDateTime.now();
    }

    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "Attendance{" + role + ", email='" + email + "', at=" + timestamp + "}";
    }
}