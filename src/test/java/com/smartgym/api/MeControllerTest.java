package com.smartgym.api;

import com.smartgym.domain.IdentityLink;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.*;
import com.smartgym.security.TestJwtDecoderConfig;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class MeControllerTest {

    static final String EMAIL = "carlos@example.com";
    static final String DNI = "74582136";

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired TrainerRepository trainers;
    @Autowired IdentityLinkRepository links;

    @BeforeEach
    void clean() {
        links.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
    }

    private ResultActions getMe(String role, String email) throws Exception {
        return mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token(role, email)));
    }

    private ResultActions onboard(String role, String email, String json) throws Exception {
        return mvc.perform(post("/api/v1/me/onboarding").header("Authorization", "Bearer " + token(role, email))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String name, Object age, String dni) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"") + ",\"age\":" + age + ",\"dni\":" + (dni == null ? "null" : "\"" + dni + "\"") + "}";
    }

    // ---------- GET /me ----------

    @Test
    void meWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerWithoutProfileIsIncomplete() throws Exception {
        getMe("cliente", EMAIL).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("cliente"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.dni").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.profile_complete").value(false))
                .andExpect(jsonPath("$.data.profile").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void customerWithProfileButNoDniIsIncomplete() throws Exception {
        customers.save(new Customer(EMAIL, "Carlos Mendoza", 28));
        getMe("cliente", EMAIL).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.name").value("Carlos Mendoza"))
                .andExpect(jsonPath("$.data.profile.age").value(28))
                .andExpect(jsonPath("$.data.dni").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.profile_complete").value(false));
    }

    @Test
    void customerWithProfileAndDniIsComplete() throws Exception {
        customers.save(new Customer(EMAIL, "Carlos Mendoza", 28));
        links.save(new IdentityLink(DNI, EMAIL));
        getMe("cliente", EMAIL).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Carlos Mendoza"))
                .andExpect(jsonPath("$.data.dni").value(DNI))
                .andExpect(jsonPath("$.data.profile_complete").value(true))
                .andExpect(jsonPath("$.data.profile.specialty").doesNotExist());
    }

    @Test
    void dniWithoutCustomerIsStillIncomplete() throws Exception {
        links.save(new IdentityLink(DNI, EMAIL));
        getMe("cliente", EMAIL).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dni").value(DNI))
                .andExpect(jsonPath("$.data.profile_complete").value(false))
                .andExpect(jsonPath("$.data.profile").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void missingRoleClaimDefaultsToCustomerAndEmailIsNormalized() throws Exception {
        customers.save(new Customer(EMAIL, "Carlos Mendoza", 28));
        getMe(null, "  Carlos@Example.COM ").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("cliente"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.name").value("Carlos Mendoza"));
    }

    @Test
    void unknownRoleDefaultsToCustomer() throws Exception {
        getMe("superuser", EMAIL).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("cliente"));
    }

    @Test
    void missingEmailClaimIs403WithClearMessage() throws Exception {
        getMe("cliente", null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("configure the session token in Clerk")));
    }

    @Test
    void trainerWithoutProfileIsIncompleteAndWithProfileIsComplete() throws Exception {
        getMe("entrenador", "lucia@smartgym.pe").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("entrenador"))
                .andExpect(jsonPath("$.data.profile_complete").value(false))
                .andExpect(jsonPath("$.data.profile").value(org.hamcrest.Matchers.nullValue()));
        trainers.save(new Trainer("lucia@smartgym.pe", "Lucía Paredes", 33, "Fuerza"));
        getMe("entrenador", "lucia@smartgym.pe").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Lucía Paredes"))
                .andExpect(jsonPath("$.data.profile.specialty").value("Fuerza"))
                .andExpect(jsonPath("$.data.profile_complete").value(true));
    }

    @Test
    void adminIsAlwaysCompleteWithoutProfile() throws Exception {
        getMe("admin", "admin@smartgym.pe").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"))
                .andExpect(jsonPath("$.data.profile_complete").value(true))
                .andExpect(jsonPath("$.data.profile").value(org.hamcrest.Matchers.nullValue()));
    }

    // ---------- POST /me/onboarding ----------

    @Test
    void validOnboardingCreatesCustomerAndLinkAndReturnsMe() throws Exception {
        onboard("cliente", EMAIL, body("Carlos Mendoza", 28, DNI)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profile_complete").value(true))
                .andExpect(jsonPath("$.data.dni").value(DNI))
                .andExpect(jsonPath("$.data.name").value("Carlos Mendoza"))
                .andExpect(jsonPath("$.data.email").value(EMAIL));
        assertTrue(customers.existsById(EMAIL));
        assertEquals(EMAIL, links.findById(DNI).orElseThrow().getEmail());
    }

    @Test
    void repeatingTheSameOnboardingIsIdempotent() throws Exception {
        onboard("cliente", EMAIL, body("Carlos Mendoza", 28, DNI)).andExpect(status().isCreated());
        onboard("cliente", EMAIL, body("Otro Nombre", 40, DNI)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Carlos Mendoza"));
        assertEquals(1, customers.count());
        assertEquals(1, links.count());
    }

    @Test
    void existingCustomerWithoutDniOnlyLinksTheDni() throws Exception {
        customers.save(new Customer(EMAIL, "Nombre Original", 30));
        onboard("cliente", EMAIL, body("Nombre Nuevo", 99, DNI)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Nombre Original"))
                .andExpect(jsonPath("$.data.profile_complete").value(true));
        assertEquals(30, customers.findById(EMAIL).orElseThrow().getAge());
    }

    @Test
    void dniLinkedToAnotherEmailIs409AndNothingIsCreated() throws Exception {
        links.save(new IdentityLink(DNI, "otra@example.com"));
        onboard("cliente", EMAIL, body("Carlos Mendoza", 28, DNI)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        assertFalse(customers.existsById(EMAIL));
        assertEquals("otra@example.com", links.findById(DNI).orElseThrow().getEmail());
    }

    @Test
    void accountAlreadyLinkedToAnotherDniIs409AndKeepsTheLink() throws Exception {
        customers.save(new Customer(EMAIL, "Carlos Mendoza", 28));
        links.save(new IdentityLink("11112222", EMAIL));
        onboard("cliente", EMAIL, body("Carlos Mendoza", 28, DNI)).andExpect(status().isConflict());
        assertTrue(links.findById(DNI).isEmpty());
        assertTrue(links.findById("11112222").isPresent());
    }

    @Test
    void invalidBodiesAre400WithFieldDetailsAndCreateNothing() throws Exception {
        String longName = "x".repeat(121);
        String[] bodies = {
                body("Carlos", 28, "1234567"), body("Carlos", 28, "123456789"), body("Carlos", 28, "1234567a"),
                body("Carlos", 28, ""), body("Carlos", 28, null), body("", 28, DNI), body(null, 28, DNI),
                body("<b>x</b>", 28, DNI), body(longName, 28, DNI), body("Carlos", -1, DNI), body("Carlos", null, DNI)
        };
        for (String b : bodies) {
            onboard("cliente", EMAIL, b).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.error.details").isArray());
        }
        assertEquals(0, customers.count());
        assertEquals(0, links.count());
    }

    @Test
    void validationErrorsNameTheOffendingField() throws Exception {
        onboard("cliente", EMAIL, body("Carlos", 28, "123")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("dni"))
                .andExpect(jsonPath("$.error.details[0].error").value("DNI must be 8 digits"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        onboard("cliente", EMAIL, "{no-json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON payload"));
    }

    @Test
    void trainerAndAdminCannotOnboard() throws Exception {
        for (String role : new String[]{"entrenador", "admin"}) {
            onboard(role, "staff@example.com", body("Staff", 30, DNI)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        assertEquals(0, customers.count());
        assertEquals(0, links.count());
    }

    @Test
    void onboardingWithoutTokenIs401AndWithoutEmailIs403() throws Exception {
        mvc.perform(post("/api/v1/me/onboarding").contentType(MediaType.APPLICATION_JSON).content(body("Carlos", 28, DNI)))
                .andExpect(status().isUnauthorized());
        onboard("cliente", null, body("Carlos", 28, DNI)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("configure the session token in Clerk")));
        assertEquals(0, customers.count());
    }

    @Test
    void onboardingNormalizesEmailAndTrimsName() throws Exception {
        onboard("cliente", "Carlos@Example.com", body("  Carlos Mendoza  ", 28, DNI)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.name").value("Carlos Mendoza"));
    }

    // ---------- POST /identity/* (no sobrescribir DNI de otro correo) ----------

    private ResultActions link(String kind, String dni, String email) throws Exception {
        return mvc.perform(post("/api/v1/identity/" + kind).header("Authorization", "Bearer " + token("admin", "admin@example.com"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dni\":\"" + dni + "\",\"email\":\"" + email + "\"}"));
    }

    @Test
    void identityLinkCannotOverwriteDniOfAnotherEmail() throws Exception {
        link("customer", DNI, EMAIL).andExpect(status().isCreated());
        link("customer", DNI, "otra@example.com").andExpect(status().isConflict());
        link("trainer", DNI, "otra@example.com").andExpect(status().isConflict());
        assertEquals(EMAIL, links.findById(DNI).orElseThrow().getEmail());
    }

    @Test
    void identityLinkIsIdempotentForTheSameEmail() throws Exception {
        link("customer", DNI, EMAIL).andExpect(status().isCreated());
        link("customer", DNI, "CARLOS@example.com").andExpect(status().isCreated());
        assertEquals(1, links.count());
    }
}
