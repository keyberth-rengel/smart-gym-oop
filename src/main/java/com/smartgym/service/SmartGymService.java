package com.smartgym.service;

import com.smartgym.model.Booking;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.CustomerRepository;
import com.smartgym.repository.TrainerRepository;
import com.smartgym.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class SmartGymService {
    private final CustomerRepository customerRepository;
    private final TrainerRepository trainerRepository;
    private final BookingRepository bookingRepository;

    public SmartGymService(CustomerRepository customerRepository,
                           TrainerRepository trainerRepository,
                           BookingRepository bookingRepository) {
        this.customerRepository = customerRepository;
        this.trainerRepository = trainerRepository;
        this.bookingRepository = bookingRepository;
    }

    public void addCustomer(Customer c) {
        String key = normalize(c.getEmail());
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Customer email must not be blank.");
        }
        if (customerRepository.existsById(key)) {
            throw new IllegalStateException("Customer already exists: " + key);
        }
        customerRepository.save(c);
    }

    public Optional<Customer> findCustomer(String email) {
        String key = normalize(email);
        return (key == null) ? Optional.empty() : customerRepository.findById(key);
    }

    public List<String> getCustomerHistory(String customerEmail) {
        return findCustomer(customerEmail)
                .map(Customer::getBookingHistory)
                .orElse(List.of());
    }

    public void addTrainer(Trainer t) {
        String key = normalize(t.getEmail());
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Trainer email must not be blank.");
        }
        if (trainerRepository.existsById(key)) {
            throw new IllegalStateException("Trainer already exists: " + key);
        }
        trainerRepository.save(t);
    }

    public Optional<Trainer> findTrainer(String email) {
        String key = normalize(email);
        return (key == null) ? Optional.empty() : trainerRepository.findById(key);
    }

    @Transactional
    public Booking createBooking(String customerEmail, String trainerEmail, LocalDate date, LocalTime time) {
        return createBooking(customerEmail, trainerEmail, date, time, null);
    }

    @Transactional
    public Booking createBooking(String customerEmail, String trainerEmail, LocalDate date, LocalTime time, String note) {
        if (customerEmail == null || trainerEmail == null || date == null || time == null) {
            throw new IllegalArgumentException("Incomplete data to create a booking.");
        }
        if (date.atTime(time).isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Bookings in the past are not allowed.");
        }

        String cKey = normalize(customerEmail);
        String tKey = normalize(trainerEmail);

        Customer customer = customerRepository.findById(cKey)
                .orElseThrow(() -> new IllegalArgumentException("Customer does not exist: " + customerEmail));
        Trainer trainer = trainerRepository.findById(tKey)
            .orElseThrow(() -> new IllegalArgumentException("Trainer does not exist: " + trainerEmail));

        Booking.Schedule schedule = new Booking.Schedule(date, time);

        Booking candidate = new Booking(customer, trainer, schedule, note);
        Booking saved;
        try {
            saved = bookingRepository.save(candidate);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Restricción única (entrenador+fecha+hora) indica horario ocupado
            throw new IllegalStateException("Trainer already has a booking at " + schedule + ".");
        }
        customer.addHistory("Booked with " + trainer.getEmail() + " at " + schedule);
        customerRepository.save(customer); // guardar historial actualizado
        return saved;
    }

    // Nuevas sobrecargas que fijan la fecha a hoy
    @Transactional
    public Booking createBookingToday(String customerEmail, String trainerEmail, LocalTime time) {
        return createBooking(customerEmail, trainerEmail, LocalDate.now(), time, null);
    }

    @Transactional
    public Booking createBookingToday(String customerEmail, String trainerEmail, LocalTime time, String note) {
        return createBooking(customerEmail, trainerEmail, LocalDate.now(), time, note);
    }

    @Transactional(readOnly = true)
    public List<Booking> listTrainerBookings(String trainerEmail, LocalDate date) {
        String key = normalize(trainerEmail);
        if (key == null) return List.of();
        List<Booking> result = bookingRepository.findByTrainer_EmailAndSchedule_Date(key, date);
        result.sort(Comparator.comparing(b -> b.getSchedule().getTime()));
        return result;
    }

    @Transactional
    public boolean cancelBooking(long bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new IllegalArgumentException("Booking not found: id=" + bookingId);
        }
        bookingRepository.deleteById(bookingId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Booking> listBookings() { return bookingRepository.findAll(); }

    private String normalize(String email) { return (email == null) ? null : email.toLowerCase().trim(); }
}