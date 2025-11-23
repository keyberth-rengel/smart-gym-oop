package com.smartgym.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "identity_links")
public class IdentityLink {
    @Id
    @Column(name = "dni", length = 32, nullable = false)
    private String dni;

    @Column(name = "email", length = 180, nullable = false)
    private String email;

    protected IdentityLink() {
    }

    public IdentityLink(String dni, String email) {
        if (dni == null || dni.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("dni and email are required");
        }
        this.dni = normalize(dni);
        this.email = normalize(email);
    }

    private String normalize(String s) { return s == null ? null : s.trim().toLowerCase(); }

    public String getDni() { return dni; }
    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = normalize(email); }
}
