package com.smartgym.api;

import com.smartgym.domain.PaymentMethod;
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

import static com.smartgym.security.TestJwtDecoderConfig.token;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class ListEndpointsTest {

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired TrainerRepository trainers;
    @Autowired BookingRepository bookings;

    @BeforeEach
    void clean() {
        bookings.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
    }

    private ResultActions call(String path, String role, String email) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token(role, email)));
    }

    // ---------- GET /trainers ----------

    /** La BD en memoria es compartida entre clases de test: no dejar datos que rompan otras limpiezas. */
    @AfterEach
    void cleanUp() {
        bookings.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
    }

    @Test
    void trainersWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/trainers")).andExpect(status().isUnauthorized());
    }

    @Test
    void trainersEmptyListIsOkNotNotFound() throws Exception {
        call("/api/v1/trainers", "cliente", "c@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void trainersAvailableToEveryRoleSortedByName() throws Exception {
        trainers.save(new Trainer("marco@x.com", "marco Vílchez", 29, "Funcional"));
        trainers.save(new Trainer("andrea@x.com", "Andrea Salas", 36, "Cardio"));
        trainers.save(new Trainer("lucia@x.com", "Lucía Paredes", 33, null));
        for (String role : new String[]{"cliente", "entrenador", "admin", "none"}) {
            call("/api/v1/trainers", role, "someone@x.com").andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(3)))
                    .andExpect(jsonPath("$.data[0].name").value("Andrea Salas"))
                    .andExpect(jsonPath("$.data[1].name").value("Lucía Paredes"))
                    .andExpect(jsonPath("$.data[2].name").value("marco Vílchez"));
        }
    }

    @Test
    void trainersExposeOnlyPublicFields() throws Exception {
        trainers.save(new Trainer("lucia@x.com", "Lucía Paredes", 33, "Fuerza"));
        call("/api/v1/trainers", "cliente", "c@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].email").value("lucia@x.com"))
                .andExpect(jsonPath("$.data[0].age").value(33))
                .andExpect(jsonPath("$.data[0].specialty").value("Fuerza"))
                .andExpect(jsonPath("$.data[0].*", hasSize(4)));
    }

    // ---------- GET /customers ----------

    @Test
    void customersWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
    }

    @Test
    void customersForbiddenForNonAdmin() throws Exception {
        for (String role : new String[]{"cliente", "entrenador", "none", "desconocido"}) {
            call("/api/v1/customers", role, "x@x.com").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Test
    void customersEmptyListForAdmin() throws Exception {
        call("/api/v1/customers", "admin", "a@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void customersSortedByNameAndWithoutSensitiveData() throws Exception {
        Customer zeta = new Customer("zeta@x.com", "zeta Ruiz", 40);
        zeta.setPaymentMethod(new PaymentMethod("4111111111111111"));
        zeta.addHistory("Booked with t@x.com at 2026-01-01 10:00");
        customers.save(zeta);
        customers.save(new Customer("carlos@x.com", "Carlos Mendoza", 28));
        customers.save(new Customer("angel@x.com", "Ángel Díaz", 31));

        String body = call("/api/v1/customers", "admin", "a@x.com").andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].name").value("Ángel Díaz"))
                .andExpect(jsonPath("$.data[1].name").value("Carlos Mendoza"))
                .andExpect(jsonPath("$.data[2].name").value("zeta Ruiz"))
                .andExpect(jsonPath("$.data[2].email").value("zeta@x.com"))
                .andExpect(jsonPath("$.data[2].age").value(40))
                .andExpect(jsonPath("$.data[2].*", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("payment_method"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("booking_history"));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains("4111"));
    }

    @Test
    void customerByEmailStillWorksForAnyAuthenticatedUser() throws Exception {
        customers.save(new Customer("carlos@x.com", "Carlos Mendoza", 28));
        call("/api/v1/customers/carlos@x.com", "cliente", "carlos@x.com").andExpect(status().isOk());
    }
}
