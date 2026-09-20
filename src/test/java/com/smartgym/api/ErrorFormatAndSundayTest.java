package com.smartgym.api;

import com.smartgym.model.Customer;
import com.smartgym.domain.IdentityLink;
import com.smartgym.repository.BookingRepository;
import com.smartgym.repository.CustomerRepository;
import com.smartgym.repository.IdentityLinkRepository;
import com.smartgym.repository.RoutineRepository;
import com.smartgym.security.TestJwtDecoderConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.smartgym.security.TestJwtDecoderConfig.token;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** B9: domingo sin bloque (antes 500) y formato ApiResponse de los errores (único advice). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class ErrorFormatAndSundayTest {

    static final String EMAIL = "sunday@x.com";
    static final String DNI = "55667788";

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired IdentityLinkRepository links;
    @Autowired RoutineRepository routines;
    @Autowired BookingRepository bookings;

    @BeforeEach
    void seed() {
        cleanUp();
        customers.save(new Customer(EMAIL, "Sunday Test", 30));
        links.save(new IdentityLink(DNI, EMAIL));
    }

    @AfterEach
    void cleanUp() {
        routines.deleteAll();
        bookings.deleteAll();
        links.deleteAll();
        customers.deleteAll();
    }

    private ResultActions as(String role, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token(role, EMAIL)));
    }

    private void assign() throws Exception {
        mvc.perform(post("/api/v1/routines/assign").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token("admin", "admin@x.com"))
                        .content("{\"dni\":\"" + DNI + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void sundayByDniReturns200WithNullBlock() throws Exception {
        assign();
        as("cliente", "/api/v1/routines/active/" + DNI + "?day=sunday")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.day").value("sunday"))
                .andExpect(jsonPath("$.data.block").value(nullValue()))
                .andExpect(jsonPath("$.data", hasKey("block")));
    }

    @Test
    void sundayByEmailReturns200WithNullBlock() throws Exception {
        assign();
        as("cliente", "/api/v1/routines/by-email/" + EMAIL + "/active?day=SUNDAY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.day").value("sunday"))
                .andExpect(jsonPath("$.data", hasKey("block")))
                .andExpect(jsonPath("$.data.block").value(nullValue()));
    }

    @Test
    void mondayStillReturnsABlock() throws Exception {
        assign();
        as("cliente", "/api/v1/routines/active/" + DNI + "?day=monday")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.block", not(emptyOrNullString())));
    }

    @Test
    void invalidDayIsStillAValidationError() throws Exception {
        assign();
        as("cliente", "/api/v1/routines/active/" + DNI + "?day=funday")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").exists());
    }

    @Test
    void errorsKeepApiResponseFormat() throws Exception {
        // 400 validación
        mvc.perform(post("/api/v1/routines/assign").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token("admin", "admin@x.com"))
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.request_id").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/routines/assign"));
        // JSON roto
        mvc.perform(post("/api/v1/routines/assign").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token("admin", "admin@x.com"))
                        .content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        // 401 sin token
        mvc.perform(get("/api/v1/trainers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        // 403 por rol
        mvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token("cliente", EMAIL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
