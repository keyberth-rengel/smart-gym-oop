package com.smartgym.service;

import com.smartgym.api.advice.NotFoundException;
import com.smartgym.model.Booking;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.CustomerRepository;
import com.smartgym.repository.TrainerCustomerRow;
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

    /** Entrenadores ordenados por nombre (sin distinguir mayúsculas ni acentos). */
    @Transactional(readOnly = true)
    public List<Trainer> listTrainers() {
        return trainerRepository.findAll().stream()
                .sorted(byName(Trainer::getName, Trainer::getEmail))
                .toList();
    }

    /** Clientes ordenados por nombre (sin distinguir mayúsculas ni acentos). */
    @Transactional(readOnly = true)
    public List<Customer> listCustomers() {
        return customerRepository.findAll().stream()
                .sorted(byName(Customer::getName, Customer::getEmail))
                .toList();
    }

    private static <T> Comparator<T> byName(java.util.function.Function<T, String> name,
                                            java.util.function.Function<T, String> email) {
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("es"));
        collator.setStrength(java.text.Collator.PRIMARY);
        return Comparator.<T, String>comparing(t -> name.apply(t) == null ? "" : name.apply(t), collator)
                .thenComparing(email, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    @Transactional
    public Booking createBooking(String customerEmail, String trainerEmail, LocalDate date, LocalTime time) {
        return createBooking(customerEmail, trainerEmail, date, time, null);
    }

    @Transactional
    public Booking createBooking(String customerEmail, String trainerEmail, LocalDate date, LocalTime time, String note) {
        return createBooking(customerEmail, trainerEmail, date, time, note, true);
    }

    private Booking createBooking(String customerEmail, String trainerEmail, LocalDate date, LocalTime time,
                                  String note, boolean strictPastCheck) {
        if (customerEmail == null || trainerEmail == null || date == null || time == null) {
            throw new IllegalArgumentException("Incomplete data to create a booking.");
        }
        if (strictPastCheck && date.atTime(time).isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Bookings in the past are not allowed.");
        }

        String cKey = normalize(customerEmail);
        String tKey = normalize(trainerEmail);

        Customer customer = customerRepository.findById(cKey)
                .orElseThrow(() -> new NotFoundException("Customer does not exist: " + customerEmail));
        Trainer trainer = trainerRepository.findById(tKey)
            .orElseThrow(() -> new NotFoundException("Trainer does not exist: " + trainerEmail));

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

    /**
     * Reserva con fecha enviada por el cliente (zona horaria local del cliente). El servidor corre en UTC:
     * la fecha debe estar a +-1 dia de la fecha UTC del servidor y la comprobacion de "pasado" es tolerante
     * (la zona mas atrasada, UTC-12, aun puede estar en esa hora de pared).
     */
    @Transactional
    public Booking createBookingForClientDate(String customerEmail, String trainerEmail, LocalDate date,
                                              LocalTime time, String note, Integer utcOffsetMinutes) {
        if (date == null || time == null) {
            throw new IllegalArgumentException("Incomplete data to create a booking.");
        }
        if (utcOffsetMinutes != null) {
            java.time.LocalDateTime clientNow = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(utcOffsetMinutes);
            requireClientToday(date, clientNow);
            if (date.atTime(time).isBefore(clientNow)) {
                throw new IllegalArgumentException("Bookings in the past are not allowed.");
            }
            return createBooking(customerEmail, trainerEmail, date, time, note, false);
        }
        requireWithinOneDayOfUtcToday(date);
        if (date.atTime(time).isBefore(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(12))) {
            throw new IllegalArgumentException("Bookings in the past are not allowed.");
        }
        return createBooking(customerEmail, trainerEmail, date, time, note, false);
    }

    public static void requireClientToday(LocalDate date, java.time.LocalDateTime clientNow) {
        if (!date.equals(clientNow.toLocalDate())) {
            throw new IllegalArgumentException("Date must be today in the client's time zone.");
        }
    }

    public static void requireWithinOneDayOfUtcToday(LocalDate date) {
        LocalDate utcToday = LocalDate.now(java.time.ZoneOffset.UTC);
        if (date.isBefore(utcToday.minusDays(1)) || date.isAfter(utcToday.plusDays(1))) {
            throw new IllegalArgumentException("Date must be today (within one day of the server's UTC date).");
        }
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

    /** Reservas del entrenador entre {@code from} y {@code to} (inclusivos; null = sin límite), por fecha y hora. */
    @Transactional(readOnly = true)
    public List<Booking> listTrainerBookings(String trainerEmail, LocalDate from, LocalDate to) {
        String key = normalize(trainerEmail);
        if (key == null) return List.of();
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'.");
        }
        return bookingRepository.findByTrainer_EmailAndSchedule_DateBetweenOrderBySchedule_DateAscSchedule_TimeAsc(
                key, from != null ? from : LocalDate.of(1900, 1, 1), to != null ? to : LocalDate.of(9999, 12, 31));
    }

    /** Clientes con al menos una reserva con el entrenador, con su total y su reserva más reciente. */
    @Transactional(readOnly = true)
    public List<TrainerCustomerRow> listTrainerCustomers(String trainerEmail) {
        Trainer trainer = findTrainer(trainerEmail)
                .orElseThrow(() -> new NotFoundException("Trainer not found: " + trainerEmail));
        return bookingRepository.findTrainerCustomers(trainer.getEmail());
    }

    /** Horas ocupadas del entrenador en una fecha (solo horas, sin datos personales). */
    @Transactional(readOnly = true)
    public List<LocalTime> listBookedTimes(String trainerEmail, LocalDate date) {
        Trainer trainer = findTrainer(trainerEmail)
                .orElseThrow(() -> new NotFoundException("Trainer not found: " + trainerEmail));
        return bookingRepository.findByTrainer_EmailAndSchedule_Date(trainer.getEmail(), date).stream()
                .map(b -> b.getSchedule().getTime())
                .sorted()
                .toList();
    }

    @Transactional
    public boolean cancelBooking(long bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new NotFoundException("Booking not found: id=" + bookingId);
        }
        bookingRepository.deleteById(bookingId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Booking> listBookings() { return bookingRepository.findAll(); }

    /** Reservas de un cliente por fecha y hora. */
    @Transactional(readOnly = true)
    public List<Booking> listBookingsForCustomer(String customerEmail) {
        String key = normalize(customerEmail);
        return key == null ? List.of() : bookingRepository.findByCustomer_EmailOrderBySchedule_DateAscSchedule_TimeAsc(key);
    }

    /** Reservas de un entrenador por fecha y hora. */
    @Transactional(readOnly = true)
    public List<Booking> listBookingsForTrainer(String trainerEmail) {
        String key = normalize(trainerEmail);
        return key == null ? List.of() : bookingRepository.findByTrainer_EmailOrderBySchedule_DateAscSchedule_TimeAsc(key);
    }

    private String normalize(String email) { return (email == null) ? null : email.toLowerCase().trim(); }
}