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

    public void addCustomer(Customer c) {
        if (customers.containsKey(c.getEmail())) {
            throw new IllegalStateException("Ya existe un cliente con ese email.");
        }
        customers.put(c.getEmail(), c);
    }

    public void addTrainer(Trainer t) {
        if (trainers.containsKey(t.getEmail())) {
            throw new IllegalStateException("Ya existe un entrenador con ese email.");
        }
        trainers.put(t.getEmail(), t);
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
            throw new IllegalArgumentException("Datos incompletos para crear la reserva.");
        }

        if (date.atTime(time).isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("No se permiten reservas en el pasado.");
        }

        Customer customer = customers.get(normalize(customerEmail));
        if (customer == null) {
            throw new IllegalArgumentException("El cliente no existe: " + customerEmail);
        }
        Trainer trainer = trainers.get(normalize(trainerEmail));
        if (trainer == null) {
            throw new IllegalArgumentException("El entrenador no existe: " + trainerEmail);
        }

        Booking.Schedule schedule = new Booking.Schedule(date, time);

        Booking candidate = new Booking(customer.getEmail(), trainer.getEmail(), schedule, note);
        boolean duplicate = bookings.stream().anyMatch(candidate::equals);
        if (duplicate) {
            throw new IllegalStateException("Reserva duplicada para " + schedule + ".");
        }

        boolean trainerBusy = bookings.stream().anyMatch(b ->
                b.getTrainerEmail().equals(normalize(trainerEmail)) &&
                        b.getSchedule().equals(schedule)
        );
        if (trainerBusy) {
            throw new IllegalStateException("El entrenador ya tiene una reserva para " + schedule + ".");
        }

        bookings.add(candidate);
        customer.addHistory("Reservó con " + trainer.getEmail() + " en " + schedule);

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
        throw new IllegalArgumentException("No existe una reserva con ID " + bookingId + ".");
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