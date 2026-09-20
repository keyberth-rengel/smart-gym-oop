package com.smartgym.service;

import com.smartgym.model.Booking;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.BookingRepository;
import com.smartgym.repository.CustomerRepository;
import com.smartgym.repository.TrainerCustomerRow;
import com.smartgym.repository.TrainerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Consulta agrupada de "clientes de un entrenador" (repositorio) y métodos nuevos del servicio. */
@SpringBootTest
@ActiveProfiles("test")
class TrainerCustomersQueryTest {

    static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    static final LocalDate D2 = LocalDate.of(2026, 9, 2);

    @Autowired BookingRepository bookings;
    @Autowired CustomerRepository customers;
    @Autowired TrainerRepository trainers;
    @Autowired SmartGymService service;

    Trainer t1, t2;
    Customer a, b, c;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
        t1 = trainers.save(new Trainer("t1@x.com", "T1", 30, null));
        t2 = trainers.save(new Trainer("t2@x.com", "T2", 30, null));
        a = customers.save(new Customer("a@x.com", "Ana", 20));
        b = customers.save(new Customer("b@x.com", "Beto", 21));
        c = customers.save(new Customer("c@x.com", "Cami", 22));
    }

    private void book(Trainer t, Customer cu, LocalDate d, String time) {
        bookings.save(new Booking(cu, t, new Booking.Schedule(d, LocalTime.parse(time))));
    }

    /** La BD en memoria es compartida entre clases de test: no dejar datos que rompan otras limpiezas. */
    @AfterEach
    void cleanUp() {
        bookings.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
    }

    @Test
    void noBookingsReturnsEmpty() {
        assertTrue(bookings.findTrainerCustomers("t1@x.com").isEmpty());
    }

    @Test
    void oneRowPerCustomerWithCountAndLatestBooking() {
        book(t1, a, D1, "10:00");
        book(t1, a, D2, "08:00");
        book(t1, a, D2, "19:00");
        book(t1, b, D1, "23:59");
        book(t2, c, D2, "10:00");   // otro entrenador
        book(t2, a, D2, "23:00");   // Ana con otro entrenador: no suma ni cambia la última con t1

        List<TrainerCustomerRow> rows = bookings.findTrainerCustomers("t1@x.com");
        assertEquals(2, rows.size());
        TrainerCustomerRow ana = rows.get(0);
        assertEquals("a@x.com", ana.email());
        assertEquals("Ana", ana.name());
        assertEquals(20, ana.age());
        assertEquals(3L, ana.sessions());
        assertEquals(D2, ana.lastDate());
        assertEquals(LocalTime.of(19, 0), ana.lastTime());
        TrainerCustomerRow beto = rows.get(1);
        assertEquals("b@x.com", beto.email());
        assertEquals(1L, beto.sessions());
        assertEquals(D1, beto.lastDate());
        assertEquals(LocalTime.of(23, 59), beto.lastTime());
    }

    @Test
    void orderedFromMostRecentToOldestAcrossCustomers() {
        book(t1, a, D1, "10:00");
        book(t1, b, D2, "09:00");
        book(t1, c, D2, "09:30");
        List<String> order = bookings.findTrainerCustomers("t1@x.com").stream().map(TrainerCustomerRow::email).toList();
        assertEquals(List.of("c@x.com", "b@x.com", "a@x.com"), order);
    }

    @Test
    void unknownTrainerEmailReturnsEmptyAtRepositoryLevel() {
        book(t1, a, D1, "10:00");
        assertTrue(bookings.findTrainerCustomers("nadie@x.com").isEmpty());
    }

    // ---- servicio ----

    @Test
    void serviceListTrainerCustomersNormalizesEmailAndFailsForUnknownTrainer() {
        book(t1, a, D1, "10:00");
        assertEquals(1, service.listTrainerCustomers("  T1@X.com ").size());
        assertThrows(IllegalArgumentException.class, () -> service.listTrainerCustomers("nadie@x.com"));
    }

    @Test
    void serviceListBookedTimesSortedAndFailsForUnknownTrainer() {
        book(t1, a, D1, "18:00");
        book(t1, b, D1, "08:30");
        book(t1, c, D2, "12:00");
        assertEquals(List.of(LocalTime.of(8, 30), LocalTime.of(18, 0)), service.listBookedTimes("T1@x.com", D1));
        assertThrows(IllegalArgumentException.class, () -> service.listBookedTimes("nadie@x.com", D1));
    }

    @Test
    void serviceListTrainerBookingsRange() {
        book(t1, a, D1, "10:00");
        book(t1, b, D2, "09:00");
        assertEquals(2, service.listTrainerBookings("t1@x.com", null, null).size());
        assertEquals(1, service.listTrainerBookings("t1@x.com", D2, null).size());
        assertEquals(1, service.listTrainerBookings("t1@x.com", null, D1).size());
        assertEquals(0, service.listTrainerBookings("t1@x.com", D2.plusDays(1), null).size());
        assertThrows(IllegalArgumentException.class, () -> service.listTrainerBookings("t1@x.com", D2, D1));
        assertTrue(service.listTrainerBookings(null, null, null).isEmpty());
    }

    @Test
    void serviceListsAreSortedByNameIgnoringCaseAndAccents() {
        customers.save(new Customer("z@x.com", "álvaro", 30));
        customers.save(new Customer("y@x.com", "Zoe", 30));
        List<String> names = service.listCustomers().stream().map(Customer::getName).toList();
        assertEquals(List.of("álvaro", "Ana", "Beto", "Cami", "Zoe"), names);
    }
}
