package com.smartgym.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Customer {
    private final String email;
    private String name;
    private int age;

    private final List<String> bookingHistory = new ArrayList<>();

    public Customer(String email, String name, int age) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("\n" + "Se requiere correo electrónico.");
        }
        this.email = email.toLowerCase().trim();
        this.name = name;
        this.age = age;
    }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public int getAge() { return age; }

    public void setName(String name) { this.name = name; }
    public void setAge(int age) { this.age = age; }

    public void addHistory(String note) {
        bookingHistory.add(note);
    }

    public List<String> getBookingHistory() {
        return Collections.unmodifiableList(bookingHistory);
    }

    @Override
    public String toString() {
        return "Customer{" +
                "email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}