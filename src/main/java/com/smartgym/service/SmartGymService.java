package com.smartgym.service;

import com.smartgym.model.Booking;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class SmartGymService {
    private final Map<String, Customer> customers = new HashMap<>();
    private final Map<String, Trainer> trainers = new HashMap<>();
    private final List<Booking> bookings = new ArrayList<>();

    private final Object bookingLock = new Object();

    public void addCustomer(Customer c) {
        String key = normalize(c.getEmail());
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Customer email must not be blank.");
        }
        if (customers.containsKey(key)) {
            throw new IllegalStateException("Customer already exists: " + key);
        }
        customers.put(key, c);
    }

    public void addTrainer(Trainer t) {
        String key = normalize(t.getEmail());
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Trainer email must not be blank.");
        }
        if (trainers.containsKey(key)) {
            throw new IllegalStateException("Trainer already exists: " + key);
        }
        trainers.put(key, t);
    }

    public Optional<Customer> findCustomer(String email) {
        return Optional.ofNullable(customers.get(normalize(email)));
    }

    public Optional<Trainer> findTrainer(String email) {
        return Optional.ofNullable(trainers.get(normalize(email)));
    }

    public Booking createBooking(String customerEmail, String trainerEmail, LocalDate date, LocalTime time) {
        return createBooking(customerEmail, trainerEmail, date, time, null);
    }

    public Booking createBooking(String customerEmail, String trainerEmail, LocalDate date, LocalTime time, String note) {
        if (customerEmail == null || trainerEmail == null || date == null || time == null) {
            throw new IllegalArgumentException("Incomplete data to create a booking.");
        }

        if (date.atTime(time).isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Bookings in the past are not allowed.");
        }

        String cKey = normalize(customerEmail);
        String tKey = normalize(trainerEmail);

        Customer customer = customers.get(cKey);
        if (customer == null) {
            throw new IllegalArgumentException("Customer does not exist: " + customerEmail);
        }
        Trainer trainer = trainers.get(tKey);
        if (trainer == null) {
            throw new IllegalArgumentException("Trainer does not exist: " + trainerEmail);
        }

        Booking.Schedule schedule = new Booking.Schedule(date, time);
        Booking candidate = new Booking(customer.getEmail(), trainer.getEmail(), schedule, note);

        synchronized (bookingLock) {
            boolean duplicate = bookings.stream().anyMatch(candidate::equals);
            if (duplicate) {
                throw new IllegalStateException("Duplicate booking for " + schedule + ".");
            }

            boolean trainerBusy = bookings.stream().anyMatch(b ->
                    b.getTrainerEmail().equals(tKey) && b.getSchedule().equals(schedule)
            );
            if (trainerBusy) {
                throw new IllegalStateException("Trainer already has a booking at " + schedule + ".");
            }

            bookings.add(candidate);
        }

        customer.addHistory("Booked with " + trainer.getEmail() + " at " + schedule);
        return candidate;
    }

    public List<Booking> listTrainerBookings(String trainerEmail, LocalDate date) {
        String key = normalize(trainerEmail);
        List<Booking> result = new ArrayList<>();
        for (Booking b : bookings) {
            if (b.getTrainerEmail().equals(key) && b.getSchedule().getDate().equals(date)) {
                result.add(b);
            }
        }
        result.sort(Comparator.comparing(b -> b.getSchedule().getTime()));
        return result;
    }

    public boolean cancelBooking(int bookingId) {
        Iterator<Booking> it = bookings.iterator();
        while (it.hasNext()) {
            Booking b = it.next();
            if (b.getId() == bookingId) {
                it.remove();
                return true;
            }
        }
        throw new IllegalArgumentException("Booking not found: id=" + bookingId);
    }

    public List<Booking> listBookings() {
        return Collections.unmodifiableList(bookings);
    }

    public List<String> getCustomerHistory(String customerEmail) {
        Customer c = customers.get(normalize(customerEmail));
        return (c != null) ? c.getBookingHistory() : List.of();
    }

    private String normalize(String email) {
        return (email == null) ? null : email.toLowerCase().trim();
    }
}