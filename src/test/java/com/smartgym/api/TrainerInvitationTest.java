package com.smartgym.api;

import com.smartgym.clerk.ClerkClient;
import com.smartgym.clerk.InvitationResult;
import com.smartgym.domain.IdentityLink;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.*;
import com.smartgym.security.TestJwtDecoderConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.smartgym.security.TestJwtDecoderConfig.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B7: alta de entrenadores con invitación en Clerk (simulada), DNI opcional, reintento y alta de clientes con DNI. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class TrainerInvitationTest {

    static final String ADMIN = "admin@x.com";

    @Autowired MockMvc mvc;
    @Autowired BookingRepository bookings;
    @Autowired ProgressRecordRepository progress;
    @Autowired RoutineRepository routines;
    @Autowired AttendanceRecordRepository attendance;
    @Autowired IdentityLinkRepository identities;
    @Autowired CustomerRepository customers;
    @Autowired TrainerRepository trainers;
    @MockitoBean ClerkClient clerk;

    @BeforeEach
    void clean() {
        bookings.deleteAll();
        progress.deleteAll();
        routines.deleteAll();
        attendance.deleteAll();
        identities.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
        when(clerk.invite(anyString(), anyString())).thenReturn(InvitationResult.invited());
    }

    ResultActions send(String path, String role, String email, String body) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path);
        if (role != null) req.header("Authorization", "Bearer " + token(role, email));
        return mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body));
    }

    static String trainerJson(String email, String dni) {
        return "{\"email\":\"" + email + "\",\"name\":\"Lucía Paredes\",\"age\":33,\"specialty\":\"Fuerza\""
                + (dni == null ? "" : ",\"dni\":\"" + dni + "\"") + "}";
    }

    // ---- POST /trainers

    @Test
    void adminCreatesTrainerWithoutDniAndInvitesThem() throws Exception {
        send("/api/v1/trainers", "admin", ADMIN, trainerJson("Lucia@X.com", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("lucia@x.com"))
                .andExpect(jsonPath("$.data.name").value("Lucía Paredes"))
                .andExpect(jsonPath("$.data.specialty").value("Fuerza"))
                .andExpect(jsonPath("$.data.dni").value(nullValue()))
                .andExpect(jsonPath("$.data.invitation.status").value("INVITED"))
                .andExpect(jsonPath("$.data.invitation.message").value("invitation_sent"));

        assertThat(trainers.findById("lucia@x.com")).isPresent();
        assertThat(identities.count()).isZero();
        verify(clerk).invite("lucia@x.com", "entrenador");
    }

    @Test
    void adminCreatesTrainerWithDniAndTheLinkIsStored() throws Exception {
        send("/api/v1/trainers", "admin", ADMIN, trainerJson("lucia@x.com", "12345678"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dni").value("12345678"));

        assertThat(identities.findById("12345678")).get().extracting(IdentityLink::getEmail).isEqualTo("lucia@x.com");
        verify(clerk).invite("lucia@x.com", "entrenador");
    }

    @Test
    void dniLinkedToAnotherEmailGives409AndCreatesNothingAndSendsNoInvitation() throws Exception {
        identities.save(new IdentityLink("12345678", "otro@x.com"));

        send("/api/v1/trainers", "admin", ADMIN, trainerJson("lucia@x.com", "12345678"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        assertThat(trainers.findById("lucia@x.com")).isEmpty();
        assertThat(identities.findById("12345678")).get().extracting(IdentityLink::getEmail).isEqualTo("otro@x.com");
        verifyNoInteractions(clerk);
    }

    @Test
    void existingTrainerGives409WithoutInviting() throws Exception {
        trainers.save(new Trainer("lucia@x.com", "Lucía", 33, "Fuerza"));

        send("/api/v1/trainers", "admin", ADMIN, trainerJson("lucia@x.com", "12345678"))
                .andExpect(status().isConflict());

        assertThat(identities.count()).isZero();
        verifyNoInteractions(clerk);
    }

    @Test
    void invalidBodiesAreRejectedWithFieldErrorsAndNoSideEffects() throws Exception {
        for (String dni : new String[]{"1234567", "123456789", "abcdefgh", "", "1234 5678"}) {
            send("/api/v1/trainers", "admin", ADMIN, trainerJson("lucia@x.com", dni))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[?(@.field=='dni')]").exists());
        }
        send("/api/v1/trainers", "admin", ADMIN, "{\"email\":\"no-es-correo\",\"name\":\"<b>\",\"age\":-1}")
                .andExpect(status().isBadRequest());
        assertThat(trainers.count()).isZero();
        verifyNoInteractions(clerk);
    }

    @Test
    void trainerIsStillCreatedWhenTheInvitationFailsOrIsSkipped() throws Exception {
        when(clerk.invite(anyString(), anyString())).thenReturn(InvitationResult.failed("clerk_unreachable"));
        send("/api/v1/trainers", "admin", ADMIN, trainerJson("a@x.com", "11111111"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invitation.status").value("FAILED"))
                .andExpect(jsonPath("$.data.invitation.message").value("clerk_unreachable"));

        when(clerk.invite(anyString(), anyString())).thenReturn(InvitationResult.skipped());
        send("/api/v1/trainers", "admin", ADMIN, trainerJson("b@x.com", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invitation.status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.invitation.message").value("clerk_not_configured"));

        assertThat(trainers.count()).isEqualTo(2);
        assertThat(identities.findById("11111111")).isPresent();
    }

    @Test
    void existingAccountRoleUpdateIsReported() throws Exception {
        when(clerk.invite(anyString(), anyString())).thenReturn(InvitationResult.roleUpdated());
        send("/api/v1/trainers", "admin", ADMIN, trainerJson("lucia@x.com", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invitation.status").value("ROLE_UPDATED"));
    }

    @Test
    void onlyAdminCanCreateTrainers() throws Exception {
        send("/api/v1/trainers", "cliente", "c@x.com", trainerJson("lucia@x.com", "12345678")).andExpect(status().isForbidden());
        send("/api/v1/trainers", "entrenador", "t@x.com", trainerJson("lucia@x.com", "12345678")).andExpect(status().isForbidden());
        send("/api/v1/trainers", "none", "n@x.com", trainerJson("lucia@x.com", "12345678")).andExpect(status().isForbidden());
        send("/api/v1/trainers", "otro", "n@x.com", trainerJson("lucia@x.com", "12345678")).andExpect(status().isForbidden());
        send("/api/v1/trainers", null, null, trainerJson("lucia@x.com", "12345678")).andExpect(status().isUnauthorized());

        assertThat(trainers.count()).isZero();
        assertThat(identities.count()).isZero();
        verifyNoInteractions(clerk);
    }

    // ---- POST /trainers/{email}/invite

    @Test
    void adminRetriesTheInvitationOfAnExistingTrainer() throws Exception {
        trainers.save(new Trainer("lucia@x.com", "Lucía", 33, "Fuerza"));
        when(clerk.invite("lucia@x.com", "entrenador")).thenReturn(InvitationResult.failed("clerk_error_500"));

        send("/api/v1/trainers/Lucia@X.com/invite", "admin", ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.message").value("clerk_error_500"));

        when(clerk.invite("lucia@x.com", "entrenador")).thenReturn(InvitationResult.invited());
        send("/api/v1/trainers/lucia@x.com/invite", "admin", ADMIN, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVITED"));

        verify(clerk, times(2)).invite("lucia@x.com", "entrenador");
    }

    @Test
    void retryForUnknownTrainerIsNotFoundLikeOtherLookupsAndDoesNotInvite() throws Exception {
        // B9 cambia este "no encontrado" de 422 a 404 (ver TrainerInvitationTest en esa rama).
        send("/api/v1/trainers/nadie@x.com/invite", "admin", ADMIN, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("Trainer not found")));
        verifyNoInteractions(clerk);
    }

    @Test
    void retryIsAdminOnlyAndChecksRoleBeforeExistence() throws Exception {
        trainers.save(new Trainer("lucia@x.com", "Lucía", 33, "Fuerza"));
        for (String target : new String[]{"lucia@x.com", "nadie@x.com"}) {
            send("/api/v1/trainers/" + target + "/invite", "cliente", "c@x.com", null).andExpect(status().isForbidden());
            send("/api/v1/trainers/" + target + "/invite", "entrenador", "lucia@x.com", null).andExpect(status().isForbidden());
            send("/api/v1/trainers/" + target + "/invite", null, null, null).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(clerk);
    }

    // ---- POST /customers con DNI opcional (no invita a nadie)

    @Test
    void adminCreatesCustomerWithDniAndNobodyIsInvited() throws Exception {
        send("/api/v1/customers", "admin", ADMIN, "{\"email\":\"Carlos@X.com\",\"name\":\"Carlos\",\"age\":28,\"dni\":\"87654321\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("carlos@x.com"))
                .andExpect(jsonPath("$.data.payment_method").doesNotExist());

        assertThat(customers.findById("carlos@x.com")).isPresent();
        assertThat(identities.findById("87654321")).get().extracting(IdentityLink::getEmail).isEqualTo("carlos@x.com");
        verifyNoInteractions(clerk);
    }

    @Test
    void adminCreatesCustomerWithoutDniAsBefore() throws Exception {
        send("/api/v1/customers", "admin", ADMIN, "{\"email\":\"carlos@x.com\",\"name\":\"Carlos\",\"age\":28}")
                .andExpect(status().isCreated());
        assertThat(identities.count()).isZero();
        verifyNoInteractions(clerk);
    }

    @Test
    void customerWithDniOfAnotherEmailGives409AndCreatesNothing() throws Exception {
        identities.save(new IdentityLink("87654321", "otro@x.com"));

        send("/api/v1/customers", "admin", ADMIN, "{\"email\":\"carlos@x.com\",\"name\":\"Carlos\",\"age\":28,\"dni\":\"87654321\"}")
                .andExpect(status().isConflict());

        assertThat(customers.findById("carlos@x.com")).isEmpty();
        assertThat(identities.findById("87654321")).get().extracting(IdentityLink::getEmail).isEqualTo("otro@x.com");
    }

    @Test
    void customerWithInvalidDniIsRejected() throws Exception {
        send("/api/v1/customers", "admin", ADMIN, "{\"email\":\"carlos@x.com\",\"name\":\"Carlos\",\"age\":28,\"dni\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='dni')]").exists());
        assertThat(customers.count()).isZero();
    }

    @Test
    void createCustomerStaysAdminOnly() throws Exception {
        send("/api/v1/customers", "cliente", "c@x.com", "{\"email\":\"carlos@x.com\",\"name\":\"Carlos\",\"age\":28,\"dni\":\"87654321\"}")
                .andExpect(status().isForbidden());
        send("/api/v1/customers", "entrenador", "t@x.com", "{\"email\":\"carlos@x.com\",\"name\":\"Carlos\",\"age\":28}")
                .andExpect(status().isForbidden());
        assertThat(customers.count()).isZero();
        assertThat(identities.count()).isZero();
    }
}
