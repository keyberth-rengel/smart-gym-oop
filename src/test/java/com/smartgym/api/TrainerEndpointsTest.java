package com.smartgym.api;

import com.smartgym.model.Booking;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.BookingRepository;
import com.smartgym.repository.CustomerRepository;
import com.smartgym.repository.TrainerRepository;
import com.smartgym.security.TestJwtDecoderConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalTime;

import static com.smartgym.security.TestJwtDecoderConfig.token;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** B4 (clientes del entrenador), B5 (reservas por fecha/rango) y disponibilidad. Cubre acceso por rol. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class TrainerEndpointsTest {

    static final String LUCIA = "lucia@x.com";
    static final String MARCO = "marco@x.com";
    static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    static final LocalDate D2 = LocalDate.of(2026, 9, 10);
    static final LocalDate D3 = LocalDate.of(2026, 9, 18);

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired TrainerRepository trainers;
    @Autowired BookingRepository bookings;

    Trainer lucia, marco;
    Customer carlos, diego, rosa, miguel;

    @BeforeEach
    void seed() {
        bookings.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
        lucia = trainers.save(new Trainer(LUCIA, "Lucía Paredes", 33, "Fuerza"));
        marco = trainers.save(new Trainer(MARCO, "Marco Vílchez", 29, "Funcional"));
        carlos = customers.save(new Customer("carlos@x.com", "Carlos Mendoza", 28));
        diego = customers.save(new Customer("diego@x.com", "Diego Quispe", 22));
        rosa = customers.save(new Customer("rosa@x.com", "Rosa Lima", 34));      // sin reservas
        miguel = customers.save(new Customer("miguel@x.com", "Miguel Torres", 45)); // solo con Marco
    }

    private void book(Trainer t, Customer c, LocalDate d, String time, String note) {
        bookings.save(new Booking(c, t, new Booking.Schedule(d, LocalTime.parse(time)), note));
    }

    private ResultActions call(String path, String role, String email) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token(role, email)));
    }

    // =================== B4: GET /trainers/{email}/customers ===================

    /** La BD en memoria es compartida entre clases de test: no dejar datos que rompan otras limpiezas. */
    @AfterEach
    void cleanUp() {
        bookings.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
    }

    @Test
    void customersWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/trainers/" + LUCIA + "/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    void customersForbiddenForCustomerRoleAndUnknownRole() throws Exception {
        for (String role : new String[]{"cliente", "none", "desconocido"}) {
            call("/api/v1/trainers/" + LUCIA + "/customers", role, LUCIA).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    void customersForbiddenForAnotherTrainer() throws Exception {
        call("/api/v1/trainers/" + LUCIA + "/customers", "entrenador", MARCO).andExpect(status().isForbidden());
    }

    @Test
    void customersTrainerWithoutEmailClaimIs403() throws Exception {
        call("/api/v1/trainers/" + LUCIA + "/customers", "entrenador", null).andExpect(status().isForbidden());
    }

    @Test
    void customersOwnTrainerCaseInsensitive() throws Exception {
        book(lucia, carlos, D1, "10:00", null);
        call("/api/v1/trainers/Lucia@X.com/customers", "entrenador", "LUCIA@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void customersAdminCanSeeAnyTrainer() throws Exception {
        book(marco, miguel, D1, "10:00", null);
        call("/api/v1/trainers/" + MARCO + "/customers", "admin", "admin@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value("miguel@x.com"));
    }

    @Test
    void customersUnknownTrainerIs422ForAdminAndForTheTrainerHimself() throws Exception {
        call("/api/v1/trainers/nadie@x.com/customers", "admin", "a@x.com").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"));
        call("/api/v1/trainers/nadie@x.com/customers", "entrenador", "nadie@x.com").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void customersTrainerWithoutBookingsGetsEmptyList() throws Exception {
        call("/api/v1/trainers/" + LUCIA + "/customers", "entrenador", LUCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void customersGroupedCountedAndOrderedByMostRecentBooking() throws Exception {
        // Carlos: 4 reservas con Lucía; la más reciente es D3 17:30 (hay otra el mismo día a las 09:00)
        book(lucia, carlos, D1, "10:00", "a");
        book(lucia, carlos, D2, "11:00", "b");
        book(lucia, carlos, D3, "09:00", "c");
        book(lucia, carlos, D3, "17:30", "d");
        // Diego: 1 reserva con Lucía en D2 (más antigua que la última de Carlos)
        book(lucia, diego, D2, "16:00", null);
        // Miguel: solo con Marco, no cuenta para Lucía; Carlos también reserva con Marco (no suma sessions)
        book(marco, miguel, D3, "12:00", null);
        book(marco, miguel, D3, "13:00", null);
        book(marco, carlos, D3, "15:00", null);

        call("/api/v1/trainers/" + LUCIA + "/customers", "entrenador", LUCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].email").value("carlos@x.com"))
                .andExpect(jsonPath("$.data[0].name").value("Carlos Mendoza"))
                .andExpect(jsonPath("$.data[0].age").value(28))
                .andExpect(jsonPath("$.data[0].sessions").value(4))
                .andExpect(jsonPath("$.data[0].last_booking_date").value("2026-09-18"))
                .andExpect(jsonPath("$.data[0].last_booking_time").value("17:30"))
                .andExpect(jsonPath("$.data[1].email").value("diego@x.com"))
                .andExpect(jsonPath("$.data[1].sessions").value(1))
                .andExpect(jsonPath("$.data[1].last_booking_date").value("2026-09-10"))
                .andExpect(jsonPath("$.data[1].last_booking_time").value("16:00"));

        call("/api/v1/trainers/" + MARCO + "/customers", "entrenador", MARCO).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].email").value("carlos@x.com"))   // 15:00 es posterior a 13:00 el mismo día
                .andExpect(jsonPath("$.data[0].sessions").value(1))
                .andExpect(jsonPath("$.data[1].email").value("miguel@x.com"))
                .andExpect(jsonPath("$.data[1].sessions").value(2))
                .andExpect(jsonPath("$.data[1].last_booking_time").value("13:00"));
    }

    @Test
    void customersExposeExactlyThePublicFields() throws Exception {
        book(lucia, carlos, D1, "10:00", null);
        String body = call("/api/v1/trainers/" + LUCIA + "/customers", "admin", "a@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].*", hasSize(6)))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("payment_method"));
        assertFalse(body.contains("booking_history"));
    }

    // =================== B5: GET /trainers/{email}/bookings ===================

    private void seedBookings() {
        book(lucia, carlos, D1, "10:00", "n1");
        book(lucia, diego, D2, "16:00", "n2");
        book(lucia, carlos, D2, "09:00", "n3");
        book(lucia, diego, D3, "18:00", null);
        book(marco, miguel, D2, "12:00", "otro entrenador");
    }

    @Test
    void bookingsWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/trainers/" + LUCIA + "/bookings")).andExpect(status().isUnauthorized());
    }

    @Test
    void bookingsForbiddenForCustomerAndOtherTrainer() throws Exception {
        call("/api/v1/trainers/" + LUCIA + "/bookings?date=2026-09-10", "cliente", "carlos@x.com").andExpect(status().isForbidden());
        call("/api/v1/trainers/" + LUCIA + "/bookings", "entrenador", MARCO).andExpect(status().isForbidden());
    }

    @Test
    void bookingsByDateSortedByTimeAndOnlyThatTrainer() throws Exception {
        seedBookings();
        call("/api/v1/trainers/" + LUCIA + "/bookings?date=2026-09-10", "entrenador", LUCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].time").value("09:00"))
                .andExpect(jsonPath("$.data[0].customer_email").value("carlos@x.com"))
                .andExpect(jsonPath("$.data[0].trainer_email").value(LUCIA))
                .andExpect(jsonPath("$.data[0].date").value("2026-09-10"))
                .andExpect(jsonPath("$.data[0].note").value("n3"))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].*", hasSize(6)))
                .andExpect(jsonPath("$.data[1].time").value("16:00"));
    }

    @Test
    void bookingsAdminSeesAnyTrainerByDate() throws Exception {
        seedBookings();
        call("/api/v1/trainers/" + MARCO + "/bookings?date=2026-09-10", "admin", "a@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].customer_email").value("miguel@x.com"));
    }

    @Test
    void bookingsWithoutParametersReturnsAllOrderedByDateThenTime() throws Exception {
        seedBookings();
        call("/api/v1/trainers/" + LUCIA + "/bookings", "entrenador", LUCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$.data[1].date").value("2026-09-10"))
                .andExpect(jsonPath("$.data[1].time").value("09:00"))
                .andExpect(jsonPath("$.data[2].time").value("16:00"))
                .andExpect(jsonPath("$.data[3].date").value("2026-09-18"));
    }

    @Test
    void bookingsFromOnly() throws Exception {
        seedBookings();
        call("/api/v1/trainers/" + LUCIA + "/bookings?from=2026-09-10", "entrenador", LUCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].date").value("2026-09-10"));
    }

    @Test
    void bookingsToOnly() throws Exception {
        seedBookings();
        call("/api/v1/trainers/" + LUCIA + "/bookings?to=2026-09-10", "entrenador", LUCIA).andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[2].date").value("2026-09-10"))
                .andExpect(jsonPath("$.data[2].time").value("16:00"));
    }

    @Test
    void bookingsRangeIsInclusiveOnBothEnds() throws Exception {
        seedBookings();
        call("/api/v1/trainers/" + LUCIA + "/bookings?from=2026-09-01&to=2026-09-10", "entrenador", LUCIA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(3)));
        call("/api/v1/trainers/" + LUCIA + "/bookings?from=2026-09-10&to=2026-09-10", "entrenador", LUCIA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2)));
        call("/api/v1/trainers/" + LUCIA + "/bookings?from=2026-09-11&to=2026-09-17", "entrenador", LUCIA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void bookingsDateTakesPrecedenceOverRange() throws Exception {
        seedBookings();
        call("/api/v1/trainers/" + LUCIA + "/bookings?date=2026-09-01&from=2026-09-10&to=2026-09-18", "entrenador", LUCIA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].date").value("2026-09-01"));
    }

    @Test
    void bookingsFromAfterToIs422() throws Exception {
        call("/api/v1/trainers/" + LUCIA + "/bookings?from=2026-09-18&to=2026-09-01", "entrenador", LUCIA)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    void bookingsInvalidDateFormatIs400() throws Exception {
        for (String q : new String[]{"date=10-09-2026", "date=abc", "from=2026-13-40", "to=xx"}) {
            call("/api/v1/trainers/" + LUCIA + "/bookings?" + q, "entrenador", LUCIA).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        }
    }

    @Test
    void bookingsUnknownTrainerKeepsReturningEmptyListForAdmin() throws Exception {
        call("/api/v1/trainers/nadie@x.com/bookings?date=2026-09-10", "admin", "a@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // =================== GET /trainers/{email}/availability ===================

    @Test
    void availabilityWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/trainers/" + LUCIA + "/availability")).andExpect(status().isUnauthorized());
    }

    @Test
    void availabilityDefaultsToTodayAndListsSortedTimes() throws Exception {
        LocalDate today = LocalDate.now();
        book(lucia, carlos, today, "18:00", "privado");
        book(lucia, diego, today, "09:30", null);
        book(lucia, carlos, today.plusDays(1), "11:00", null);
        book(marco, miguel, today, "12:00", null);

        call("/api/v1/trainers/" + LUCIA + "/availability", "cliente", "rosa@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(today.toString()))
                .andExpect(jsonPath("$.data.booked_times", contains("09:30", "18:00")))
                .andExpect(jsonPath("$.data.*", hasSize(2)));
    }

    @Test
    void availabilityForGivenDateAvailableToEveryRoleWithoutPersonalData() throws Exception {
        seedBookings();
        for (String role : new String[]{"cliente", "entrenador", "admin", "none"}) {
            String body = call("/api/v1/trainers/" + LUCIA + "/availability?date=2026-09-10", role, "someone@x.com")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.date").value("2026-09-10"))
                    .andExpect(jsonPath("$.data.booked_times", contains("09:00", "16:00")))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("carlos"));
            assertFalse(body.contains("diego"));
            assertFalse(body.contains("n2"));
        }
    }

    @Test
    void availabilityEmptyDayIsEmptyList() throws Exception {
        call("/api/v1/trainers/" + LUCIA + "/availability?date=2030-01-01", "cliente", "c@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booked_times", hasSize(0)));
    }

    @Test
    void availabilityUnknownTrainerIs422() throws Exception {
        call("/api/v1/trainers/nadie@x.com/availability", "cliente", "c@x.com").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void availabilityInvalidDateIs400() throws Exception {
        call("/api/v1/trainers/" + LUCIA + "/availability?date=mañana", "cliente", "c@x.com").andExpect(status().isBadRequest());
    }

    @Test
    void availabilityEmailIsCaseInsensitive() throws Exception {
        book(lucia, carlos, D1, "10:00", null);
        call("/api/v1/trainers/LUCIA@X.COM/availability?date=2026-09-01", "cliente", "c@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booked_times", contains("10:00")));
    }
}
