package com.smartgym.model;

public class Trainer {
    private final String email;
    private String name;
    private int age;
    private String specialty;

    public Trainer(String email, String name, int age, String specialty) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("\n" + "Se requiere correo electrónico.");
        }
        this.email = email.toLowerCase().trim();
        this.name = name;
        this.age = age;
        this.specialty = specialty;
    }

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