package com.smartgym.api;

import com.jayway.jsonpath.JsonPath;
import com.smartgym.application.GymExtensions;
import com.smartgym.domain.AttendanceRecord;
import com.smartgym.domain.IdentityLink;
import com.smartgym.domain.ProgressRecord;
import com.smartgym.model.Booking;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.*;
import com.smartgym.security.TestJwtDecoderConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static com.smartgym.security.TestJwtDecoderConfig.token;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B8 y B6: matriz de acceso por rol de TODOS los endpoints, más el contenido filtrado, la no filtración de existencia
 * en los 403, los DTO sin datos sensibles y la asignación de rutinas por DNI o correo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class AccessMatrixTest {

    static final String CARLOS = "carlos@x.com", DIEGO = "diego@x.com", ROSA = "rosa@x.com";
    static final String LUCIA = "lucia@x.com", MARCO = "marco@x.com", ADMIN = "admin@x.com";
    static final String DNI_CARLOS = "11111111", DNI_DIEGO = "22222222", DNI_ROSA = "33333333";
    static final String DNI_LUCIA = "99999999", DNI_MARCO = "88888888", DNI_UNLINKED = "00000000";
    static final LocalDate D1 = LocalDate.of(2026, 9, 1), D2 = LocalDate.of(2026, 9, 10);

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired TrainerRepository trainers;
    @Autowired BookingRepository bookings;
    @Autowired IdentityLinkRepository links;
    @Autowired RoutineRepository routines;
    @Autowired ProgressRecordRepository progress;
    @Autowired AttendanceRecordRepository attendance;
    @Autowired GymExtensions ext;

    Booking bLuciaCarlos, bMarcoDiego, bLuciaDiego;

    @BeforeEach
    void seed() {
        cleanUp();
        trainers.save(new Trainer(LUCIA, "Lucía Paredes", 33, "Fuerza"));
        trainers.save(new Trainer(MARCO, "Marco Vílchez", 29, "Funcional"));
        var carlos = customers.save(new Customer(CARLOS, "Carlos Mendoza", 28));
        var diego = customers.save(new Customer(DIEGO, "Diego Quispe", 22));
        customers.save(new Customer(ROSA, "Rosa Lima", 34)); // sin reservas
        links.save(new IdentityLink(DNI_CARLOS, CARLOS));
        links.save(new IdentityLink(DNI_DIEGO, DIEGO));
        links.save(new IdentityLink(DNI_ROSA, ROSA));
        links.save(new IdentityLink(DNI_LUCIA, LUCIA));
        links.save(new IdentityLink(DNI_MARCO, MARCO));
        var lucia = trainers.findById(LUCIA).orElseThrow();
        var marco = trainers.findById(MARCO).orElseThrow();
        // Lucía atiende a Carlos y a Diego; Marco solo a Diego (Marco NO tiene relación con Carlos).
        bLuciaCarlos = bookings.save(new Booking(carlos, lucia, new Booking.Schedule(D1, LocalTime.parse("10:00")), "Piernas"));
        bMarcoDiego = bookings.save(new Booking(diego, marco, new Booking.Schedule(D1, LocalTime.parse("11:00")), null));
        bLuciaDiego = bookings.save(new Booking(diego, lucia, new Booking.Schedule(D2, LocalTime.parse("12:00")), null));
        ext.assignRandomRoutine(CARLOS);
        ext.assignRandomRoutine(DIEGO);
        progress.save(new ProgressRecord(carlos, D1, 75, 19, 41));
        progress.save(new ProgressRecord(diego, D1, 80, 22, 38));
        attendance.save(new AttendanceRecord(CARLOS, AttendanceRecord.Role.CUSTOMER));
    }

    /** La BD en memoria es compartida entre clases de test: dejarla vacía y en orden seguro por las FK. */
    @AfterEach
    void cleanUp() {
        attendance.deleteAll();
        progress.deleteAll();
        routines.deleteAll();
        bookings.deleteAll();
        links.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
    }

    // =============================== infraestructura ===============================

    /** who: quién llama (ver {@link #bearer}). status: código HTTP esperado. */
    record Case(String name, String method, String path, String body, String who, int status) {
        @Override public String toString() { return name; }
    }

    static String bearer(String who) {
        return switch (who) {
            case "ANON" -> null;
            case "ADMIN" -> token("admin", ADMIN);
            case "ADMIN_NOEMAIL" -> token("admin", null);
            case "CARLOS" -> token("cliente", CARLOS);
            case "CARLOS_UPPER" -> token("cliente", "CARLOS@X.COM");
            case "CARLOS_NOROLE" -> token(null, CARLOS);
            case "CARLOS_UNKNOWNROLE" -> token("superusuario", CARLOS);
            case "CARLOS_NOEMAIL" -> token("cliente", null);
            case "ROSA" -> token("cliente", ROSA);
            case "LUCIA" -> token("entrenador", LUCIA);
            case "LUCIA_UPPER" -> token("entrenador", "LUCIA@X.COM");
            case "MARCO" -> token("entrenador", MARCO);
            default -> throw new IllegalArgumentException(who);
        };
    }

    private MvcResult call(String method, String path, String body, String who) throws Exception {
        MockHttpServletRequestBuilder rb = request(HttpMethod.valueOf(method), path);
        String b = bearer(who);
        if (b != null) rb.header("Authorization", "Bearer " + b);
        if (body != null) rb.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(rb).andReturn();
    }

    private static Case c(String method, String path, String body, String who, int status) {
        return new Case(who + " " + method + " " + path + (body == null ? "" : " " + body) + " -> " + status,
                method, path, body, who, status);
    }

    // =============================== la matriz ===============================

    static Stream<Case> matrix() {
        String prog = "{\"dni\":\"" + DNI_CARLOS + "\",\"weightKg\":70,\"bodyFatPct\":20,\"musclePct\":40}";
        String bookCarlos = "{\"customer_email\":\"" + CARLOS + "\",\"trainer_email\":\"" + LUCIA + "\",\"time\":\"23:59\"}";
        String bookCarlosUpper = "{\"customer_email\":\"CARLOS@x.com\",\"trainer_email\":\"" + LUCIA + "\",\"time\":\"23:58\"}";
        return Stream.of(
                // ---- GET /bookings (filtrado por rol; el detalle del filtrado se verifica aparte)
                c("GET", "/api/v1/bookings", null, "ADMIN", 200), c("GET", "/api/v1/bookings", null, "CARLOS", 200),
                c("GET", "/api/v1/bookings", null, "LUCIA", 200), c("GET", "/api/v1/bookings", null, "ROSA", 200),
                c("GET", "/api/v1/bookings", null, "CARLOS_NOROLE", 200), c("GET", "/api/v1/bookings", null, "CARLOS_NOEMAIL", 403),
                c("GET", "/api/v1/bookings", null, "ANON", 401),
                // ---- POST /bookings
                c("POST", "/api/v1/bookings", bookCarlos, "CARLOS", 201),
                c("POST", "/api/v1/bookings", bookCarlos, "CARLOS_NOROLE", 201),
                c("POST", "/api/v1/bookings", bookCarlos, "CARLOS_UNKNOWNROLE", 201),
                c("POST", "/api/v1/bookings", bookCarlosUpper, "CARLOS_UPPER", 201),
                c("POST", "/api/v1/bookings", bookCarlos, "ADMIN", 201),
                c("POST", "/api/v1/bookings", bookCarlos, "ROSA", 403),     // a nombre de otro
                c("POST", "/api/v1/bookings", bookCarlos, "LUCIA", 403),    // el entrenador no reserva
                c("POST", "/api/v1/bookings", bookCarlos, "MARCO", 403),
                c("POST", "/api/v1/bookings", bookCarlos, "CARLOS_NOEMAIL", 403),
                c("POST", "/api/v1/bookings", bookCarlos, "ANON", 401),
                // ---- DELETE /bookings/{id}: solo admin (y sin filtrar existencia)
                c("DELETE", "/api/v1/bookings/{B}", null, "ADMIN", 204),
                c("DELETE", "/api/v1/bookings/{B}", null, "CARLOS", 403),
                c("DELETE", "/api/v1/bookings/{B}", null, "LUCIA", 403),
                c("DELETE", "/api/v1/bookings/{B}", null, "ANON", 401),
                c("DELETE", "/api/v1/bookings/999999", null, "ADMIN", 422),
                c("DELETE", "/api/v1/bookings/999999", null, "CARLOS", 403),
                // ---- customers
                c("POST", "/api/v1/customers", "{\"email\":\"nuevo@x.com\",\"name\":\"Nuevo\",\"age\":30}", "ADMIN", 201),
                c("POST", "/api/v1/customers", "{\"email\":\"nuevo@x.com\",\"name\":\"Nuevo\",\"age\":30}", "CARLOS", 403),
                c("POST", "/api/v1/customers", "{\"email\":\"nuevo@x.com\",\"name\":\"Nuevo\",\"age\":30}", "LUCIA", 403),
                c("POST", "/api/v1/customers", "{\"email\":\"nuevo@x.com\",\"name\":\"Nuevo\",\"age\":30}", "ANON", 401),
                c("GET", "/api/v1/customers", null, "ADMIN", 200), c("GET", "/api/v1/customers", null, "CARLOS", 403),
                c("GET", "/api/v1/customers", null, "LUCIA", 403),
                c("GET", "/api/v1/customers/" + CARLOS, null, "ADMIN", 200),
                c("GET", "/api/v1/customers/" + CARLOS, null, "CARLOS", 200),
                c("GET", "/api/v1/customers/" + CARLOS, null, "CARLOS_UPPER", 200),
                c("GET", "/api/v1/customers/CARLOS@X.COM", null, "CARLOS", 200),
                c("GET", "/api/v1/customers/" + CARLOS, null, "CARLOS_NOROLE", 200),
                c("GET", "/api/v1/customers/" + CARLOS, null, "CARLOS_UNKNOWNROLE", 200),
                c("GET", "/api/v1/customers/" + CARLOS, null, "CARLOS_NOEMAIL", 403),
                c("GET", "/api/v1/customers/" + CARLOS, null, "ROSA", 403),
                c("GET", "/api/v1/customers/" + CARLOS, null, "LUCIA", 200),      // su cliente
                c("GET", "/api/v1/customers/" + CARLOS, null, "LUCIA_UPPER", 200),
                c("GET", "/api/v1/customers/" + CARLOS, null, "MARCO", 403),      // sin relación
                c("GET", "/api/v1/customers/" + CARLOS, null, "ANON", 401),
                c("GET", "/api/v1/customers/ghost@x.com", null, "ADMIN", 422),
                c("GET", "/api/v1/customers/ghost@x.com", null, "LUCIA", 403),
                c("GET", "/api/v1/customers/ghost@x.com", null, "CARLOS", 403),
                c("GET", "/api/v1/customers/by-dni/" + DNI_CARLOS, null, "ADMIN", 200),
                c("GET", "/api/v1/customers/by-dni/" + DNI_CARLOS, null, "CARLOS", 200),
                c("GET", "/api/v1/customers/by-dni/" + DNI_CARLOS, null, "ROSA", 403),
                c("GET", "/api/v1/customers/by-dni/" + DNI_CARLOS, null, "LUCIA", 200),
                c("GET", "/api/v1/customers/by-dni/" + DNI_CARLOS, null, "MARCO", 403),
                c("GET", "/api/v1/customers/by-dni/" + DNI_CARLOS, null, "ANON", 401),
                c("GET", "/api/v1/customers/by-dni/" + DNI_UNLINKED, null, "ADMIN", 404),
                c("GET", "/api/v1/customers/by-dni/" + DNI_UNLINKED, null, "CARLOS", 403),
                // ---- trainers
                c("POST", "/api/v1/trainers", "{\"email\":\"nuevo.t@x.com\",\"name\":\"Nuevo T\",\"age\":30,\"specialty\":\"Yoga\"}", "ADMIN", 201),
                c("POST", "/api/v1/trainers", "{\"email\":\"nuevo.t@x.com\",\"name\":\"Nuevo T\",\"age\":30,\"specialty\":\"Yoga\"}", "CARLOS", 403),
                c("POST", "/api/v1/trainers", "{\"email\":\"nuevo.t@x.com\",\"name\":\"Nuevo T\",\"age\":30,\"specialty\":\"Yoga\"}", "LUCIA", 403),
                c("POST", "/api/v1/trainers", "{\"email\":\"nuevo.t@x.com\",\"name\":\"Nuevo T\",\"age\":30,\"specialty\":\"Yoga\"}", "ANON", 401),
                c("GET", "/api/v1/trainers/" + LUCIA, null, "CARLOS", 200), c("GET", "/api/v1/trainers/" + LUCIA, null, "LUCIA", 200),
                c("GET", "/api/v1/trainers/" + LUCIA, null, "MARCO", 200), c("GET", "/api/v1/trainers/" + LUCIA, null, "ADMIN", 200),
                c("GET", "/api/v1/trainers/" + LUCIA, null, "ANON", 401),
                // ---- identity
                c("POST", "/api/v1/identity/customer", "{\"dni\":\"55555555\",\"email\":\"nuevo@x.com\"}", "ADMIN", 201),
                c("POST", "/api/v1/identity/customer", "{\"dni\":\"55555555\",\"email\":\"nuevo@x.com\"}", "CARLOS", 403),
                c("POST", "/api/v1/identity/customer", "{\"dni\":\"55555555\",\"email\":\"nuevo@x.com\"}", "LUCIA", 403),
                c("POST", "/api/v1/identity/customer", "{\"dni\":\"55555555\",\"email\":\"nuevo@x.com\"}", "ANON", 401),
                c("POST", "/api/v1/identity/trainer", "{\"dni\":\"55555556\",\"email\":\"nuevo.t@x.com\"}", "ADMIN", 201),
                c("POST", "/api/v1/identity/trainer", "{\"dni\":\"55555556\",\"email\":\"nuevo.t@x.com\"}", "CARLOS", 403),
                c("POST", "/api/v1/identity/trainer", "{\"dni\":\"55555556\",\"email\":\"nuevo.t@x.com\"}", "LUCIA", 403),
                c("GET", "/api/v1/identity/" + DNI_CARLOS, null, "ADMIN", 200),
                c("GET", "/api/v1/identity/" + DNI_CARLOS, null, "CARLOS", 200),
                c("GET", "/api/v1/identity/" + DNI_CARLOS, null, "CARLOS_UPPER", 200),
                c("GET", "/api/v1/identity/" + DNI_CARLOS, null, "ROSA", 403),
                c("GET", "/api/v1/identity/" + DNI_CARLOS, null, "LUCIA", 403),   // ni su entrenador lo resuelve
                c("GET", "/api/v1/identity/" + DNI_LUCIA, null, "LUCIA", 200),    // el suyo sí
                c("GET", "/api/v1/identity/" + DNI_UNLINKED, null, "CARLOS", 403),
                c("GET", "/api/v1/identity/" + DNI_UNLINKED, null, "ADMIN", 404),
                c("GET", "/api/v1/identity/" + DNI_CARLOS, null, "ANON", 401),
                // ---- progress
                c("POST", "/api/v1/progress", prog, "CARLOS", 201), c("POST", "/api/v1/progress", prog, "ADMIN", 201),
                c("POST", "/api/v1/progress", prog, "CARLOS_UPPER", 201),
                c("POST", "/api/v1/progress", prog, "ROSA", 403),     // DNI de otro
                c("POST", "/api/v1/progress", prog, "LUCIA", 403), c("POST", "/api/v1/progress", prog, "MARCO", 403),
                c("POST", "/api/v1/progress", prog, "ANON", 401),
                c("POST", "/api/v1/progress", prog.replace(DNI_CARLOS, DNI_UNLINKED), "CARLOS", 403),
                c("POST", "/api/v1/progress", prog.replace(DNI_CARLOS, DNI_UNLINKED), "ADMIN", 422),
                c("GET", "/api/v1/progress/" + DNI_CARLOS, null, "CARLOS", 200), c("GET", "/api/v1/progress/" + DNI_CARLOS, null, "ADMIN", 200),
                c("GET", "/api/v1/progress/" + DNI_CARLOS, null, "LUCIA", 200), c("GET", "/api/v1/progress/" + DNI_CARLOS, null, "ROSA", 403),
                c("GET", "/api/v1/progress/" + DNI_CARLOS, null, "MARCO", 403), c("GET", "/api/v1/progress/" + DNI_CARLOS, null, "ANON", 401),
                c("GET", "/api/v1/progress/" + DNI_UNLINKED, null, "CARLOS", 403), c("GET", "/api/v1/progress/" + DNI_UNLINKED, null, "ADMIN", 422),
                c("GET", "/api/v1/progress/by-email/" + CARLOS, null, "CARLOS", 200),
                c("GET", "/api/v1/progress/by-email/CARLOS@X.COM", null, "CARLOS", 200),
                c("GET", "/api/v1/progress/by-email/" + CARLOS, null, "ADMIN", 200),
                c("GET", "/api/v1/progress/by-email/" + CARLOS, null, "LUCIA", 200), c("GET", "/api/v1/progress/by-email/" + CARLOS, null, "LUCIA_UPPER", 200),
                c("GET", "/api/v1/progress/by-email/" + CARLOS, null, "MARCO", 403), c("GET", "/api/v1/progress/by-email/" + CARLOS, null, "ROSA", 403),
                c("GET", "/api/v1/progress/by-email/" + CARLOS, null, "ANON", 401),
                c("GET", "/api/v1/progress/by-email/ghost@x.com", null, "LUCIA", 403),
                // ---- routines: assign
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_CARLOS + "\"}", "ADMIN", 201),
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_CARLOS + "\"}", "LUCIA", 201),     // formato anterior
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_CARLOS + "\"}", "MARCO", 403),
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_CARLOS + "\"}", "CARLOS", 403),
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_CARLOS + "\"}", "ROSA", 403),
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_CARLOS + "\"}", "ANON", 401),
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_ROSA + "\"}", "LUCIA", 403),        // Rosa no es su cliente
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_UNLINKED + "\"}", "LUCIA", 403),
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_UNLINKED + "\"}", "ADMIN", 422),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "ADMIN", 201),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "LUCIA", 201),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"CARLOS@X.COM\"}", "LUCIA_UPPER", 201),
                c("POST", "/api/v1/routines/assign", "{\"customerEmail\":\"" + CARLOS + "\"}", "ADMIN", 201),   // alias camelCase
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"\",\"customer_email\":\"" + CARLOS + "\"}", "LUCIA", 201), // vacío = ausente
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "MARCO", 403),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "CARLOS", 403),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + ROSA + "\"}", "LUCIA", 403),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"ghost@x.com\"}", "LUCIA", 403),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"ghost@x.com\"}", "ADMIN", 422),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "ANON", 401),
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_CARLOS + "\",\"customer_email\":\"" + CARLOS + "\"}", "ADMIN", 400), // ambos
                c("POST", "/api/v1/routines/assign", "{}", "ADMIN", 400),                                                                    // ninguno
                c("POST", "/api/v1/routines/assign", "{\"dni\":\"  \"}", "ADMIN", 400),
                c("POST", "/api/v1/routines/assign", "{\"customer_email\":\"no-es-correo\"}", "ADMIN", 400),
                // ---- routines: lecturas por DNI y por correo
                c("GET", "/api/v1/routines/history/" + DNI_CARLOS, null, "CARLOS", 200), c("GET", "/api/v1/routines/history/" + DNI_CARLOS, null, "ADMIN", 200),
                c("GET", "/api/v1/routines/history/" + DNI_CARLOS, null, "LUCIA", 200), c("GET", "/api/v1/routines/history/" + DNI_CARLOS, null, "ROSA", 403),
                c("GET", "/api/v1/routines/history/" + DNI_CARLOS, null, "MARCO", 403), c("GET", "/api/v1/routines/history/" + DNI_CARLOS, null, "ANON", 401),
                c("GET", "/api/v1/routines/history/" + DNI_UNLINKED, null, "CARLOS", 403), c("GET", "/api/v1/routines/history/" + DNI_UNLINKED, null, "ADMIN", 422),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/history", null, "CARLOS", 200),
                c("GET", "/api/v1/routines/by-email/CARLOS@X.COM/history", null, "CARLOS", 200),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/history", null, "ADMIN", 200),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/history", null, "LUCIA", 200),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/history", null, "MARCO", 403),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/history", null, "ROSA", 403),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/history", null, "ANON", 401),
                c("GET", "/api/v1/routines/active/" + DNI_CARLOS + "?day=monday", null, "CARLOS", 200),
                c("GET", "/api/v1/routines/active/" + DNI_CARLOS + "?day=monday", null, "ADMIN", 200),
                c("GET", "/api/v1/routines/active/" + DNI_CARLOS + "?day=monday", null, "LUCIA", 200),
                c("GET", "/api/v1/routines/active/" + DNI_CARLOS + "?day=monday", null, "MARCO", 403),
                c("GET", "/api/v1/routines/active/" + DNI_CARLOS + "?day=monday", null, "ROSA", 403),
                c("GET", "/api/v1/routines/active/" + DNI_CARLOS + "?day=monday", null, "ANON", 401),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/active?day=friday", null, "CARLOS", 200),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/active?day=friday", null, "LUCIA", 200),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/active?day=friday", null, "ADMIN", 200),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/active?day=friday", null, "MARCO", 403),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/active?day=friday", null, "ROSA", 403),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/active?day=friday", null, "ANON", 401),
                c("GET", "/api/v1/routines/by-email/" + CARLOS + "/active?day=notaday", null, "CARLOS", 422),
                // ---- attendance
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_CARLOS + "\"}", "CARLOS", 200),
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_CARLOS + "\"}", "CARLOS_UPPER", 200),
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_CARLOS + "\"}", "ADMIN", 200),
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_CARLOS + "\"}", "ROSA", 403),
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_CARLOS + "\"}", "LUCIA", 403),
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_LUCIA + "\"}", "LUCIA", 200),     // el entrenador registra el suyo
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_MARCO + "\"}", "LUCIA", 403),
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_UNLINKED + "\"}", "CARLOS", 403),
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_UNLINKED + "\"}", "ADMIN", 422),
                c("POST", "/api/v1/access", "{\"dni\":\"" + DNI_CARLOS + "\"}", "ANON", 401),
                c("GET", "/api/v1/attendance/" + DNI_CARLOS, null, "CARLOS", 200), c("GET", "/api/v1/attendance/" + DNI_CARLOS, null, "ADMIN", 200),
                c("GET", "/api/v1/attendance/" + DNI_CARLOS, null, "ROSA", 403), c("GET", "/api/v1/attendance/" + DNI_CARLOS, null, "LUCIA", 403),
                c("GET", "/api/v1/attendance/" + DNI_CARLOS, null, "ANON", 401),
                c("GET", "/api/v1/attendance/" + DNI_LUCIA, null, "LUCIA", 200),
                c("GET", "/api/v1/attendance/" + DNI_UNLINKED, null, "CARLOS", 403), c("GET", "/api/v1/attendance/" + DNI_UNLINKED, null, "ADMIN", 422),
                // ---- admin sin claim email: los endpoints solo-admin no lo necesitan; los de datos propios tampoco (admin pasa antes)
                c("GET", "/api/v1/customers/" + CARLOS, null, "ADMIN_NOEMAIL", 200),
                c("GET", "/api/v1/bookings", null, "ADMIN_NOEMAIL", 200)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void matrixCase(Case tc) throws Exception {
        String path = tc.path()
                .replace("{B}", String.valueOf(bLuciaCarlos.getId()));
        MvcResult r = call(tc.method(), path, tc.body(), tc.who());
        assertEquals(tc.status(), r.getResponse().getStatus(),
                () -> tc.name() + " body=" + safe(r));
    }

    private static String safe(MvcResult r) {
        try { return r.getResponse().getContentAsString(); } catch (Exception e) { return "?"; }
    }

    // ---- 401 y 403 con el formato estándar ApiResponse

    @Test
    void unauthorizedAndForbiddenUseTheStandardEnvelope() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/v1/routines/by-email/" + CARLOS + "/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(request(HttpMethod.GET, "/api/v1/routines/by-email/" + CARLOS + "/history")
                        .header("Authorization", "Bearer " + bearer("ROSA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.request_id").exists());
    }

    // =============================== filtrado de GET /bookings ===============================

    private List<Integer> bookingIds(String who) throws Exception {
        MvcResult r = call("GET", "/api/v1/bookings", null, who);
        assertEquals(200, r.getResponse().getStatus());
        return JsonPath.read(r.getResponse().getContentAsString(), "$.data[*].id");
    }

    @Test
    void bookingsAreFilteredByRole() throws Exception {
        int lc = bLuciaCarlos.getId().intValue(), md = bMarcoDiego.getId().intValue(), ld = bLuciaDiego.getId().intValue();
        assertEquals(3, bookingIds("ADMIN").size());
        assertTrue(bookingIds("ADMIN").containsAll(List.of(lc, md, ld)));
        assertEquals(List.of(lc), bookingIds("CARLOS"));            // solo las suyas como cliente
        assertEquals(List.of(lc), bookingIds("CARLOS_UPPER"));
        assertEquals(List.of(lc, ld), bookingIds("LUCIA"));         // solo las suyas como entrenador, por fecha
        assertEquals(List.of(md), bookingIds("MARCO"));
        assertEquals(List.of(), bookingIds("ROSA"));                // sin reservas: lista vacía, no error
    }

    @Test
    void bookingResponseShapeIsUnchanged() throws Exception {
        MvcResult r = call("GET", "/api/v1/bookings", null, "CARLOS");
        String json = r.getResponse().getContentAsString();
        assertEquals(CARLOS, JsonPath.read(json, "$.data[0].customer_email"));
        assertEquals(LUCIA, JsonPath.read(json, "$.data[0].trainer_email"));
        assertEquals("2026-09-01", JsonPath.read(json, "$.data[0].date"));
        assertNotNull(JsonPath.read(json, "$.data[0].time"));
    }

    @Test
    void createdBookingBelongsToTheCallerAndTheAdminCanCancel() throws Exception {
        long before = bookings.count();
        MvcResult r = call("POST", "/api/v1/bookings",
                "{\"customer_email\":\"" + ROSA + "\",\"trainer_email\":\"" + MARCO + "\",\"time\":\"23:57\"}", "ROSA");
        assertEquals(201, r.getResponse().getStatus());
        assertEquals(before + 1, bookings.count());
        Integer id = JsonPath.read(r.getResponse().getContentAsString(), "$.data.id");
        assertEquals(403, call("DELETE", "/api/v1/bookings/" + id, null, "ROSA").getResponse().getStatus());
        assertEquals(before + 1, bookings.count());
        assertEquals(204, call("DELETE", "/api/v1/bookings/" + id, null, "ADMIN").getResponse().getStatus());
        assertEquals(before, bookings.count());
        // una reserva ajena rechazada no crea nada
        assertEquals(403, call("POST", "/api/v1/bookings",
                "{\"customer_email\":\"" + CARLOS + "\",\"trainer_email\":\"" + MARCO + "\",\"time\":\"23:56\"}", "ROSA").getResponse().getStatus());
        assertEquals(before, bookings.count());
    }

    // =============================== DTO sin datos sensibles ===============================

    @Test
    void customerResponsesOnlyExposeEmailNameAndAge() throws Exception {
        for (String path : new String[]{"/api/v1/customers/" + CARLOS, "/api/v1/customers/by-dni/" + DNI_CARLOS}) {
            String json = call("GET", path, null, "CARLOS").getResponse().getContentAsString();
            java.util.Map<String, Object> data = JsonPath.read(json, "$.data");
            assertEquals(java.util.Set.of("email", "name", "age"), data.keySet(), path);
        }
        MvcResult created = call("POST", "/api/v1/customers", "{\"email\":\"nuevo@x.com\",\"name\":\"Nuevo\",\"age\":30}", "ADMIN");
        java.util.Map<String, Object> data = JsonPath.read(created.getResponse().getContentAsString(), "$.data");
        assertEquals(java.util.Set.of("email", "name", "age"), data.keySet());
        assertFalse(created.getResponse().getContentAsString().contains("booking_history"));
        assertFalse(created.getResponse().getContentAsString().contains("payment_method"));
    }

    @Test
    void trainerResponsesUseTheDto() throws Exception {
        java.util.Map<String, Object> data = JsonPath.read(
                call("GET", "/api/v1/trainers/" + LUCIA, null, "CARLOS").getResponse().getContentAsString(), "$.data");
        assertEquals(java.util.Set.of("email", "name", "age", "specialty"), data.keySet());
        data = JsonPath.read(call("POST", "/api/v1/trainers",
                "{\"email\":\"nuevo.t@x.com\",\"name\":\"Nuevo T\",\"age\":30,\"specialty\":\"Yoga\"}", "ADMIN")
                .getResponse().getContentAsString(), "$.data");
        // El alta añade el DNI (opcional) y el resultado de la invitación en Clerk; nunca datos sensibles.
        assertEquals(java.util.Set.of("email", "name", "age", "specialty", "dni", "invitation"), data.keySet());
    }

    // =============================== el 403 no filtra existencia ===============================

    private String forbiddenSignature(String method, String path, String body, String who) throws Exception {
        MvcResult r = call(method, path, body, who);
        assertEquals(403, r.getResponse().getStatus(), path);
        String json = r.getResponse().getContentAsString();
        // mismo código y mismo mensaje; solo cambian timestamp, path y request_id
        return JsonPath.read(json, "$.error.code") + "|" + JsonPath.read(json, "$.message") + "|"
                + JsonPath.<Object>read(json, "$.success");
    }

    @Test
    void forbiddenLooksTheSameForExistingAndMissingResources() throws Exception {
        // ROSA consulta datos de Carlos (existe) y de un cliente inexistente (ghost): idéntico 403.
        assertEquals(forbiddenSignature("GET", "/api/v1/customers/" + CARLOS, null, "ROSA"),
                forbiddenSignature("GET", "/api/v1/customers/ghost@x.com", null, "ROSA"));
        assertEquals(forbiddenSignature("GET", "/api/v1/customers/by-dni/" + DNI_CARLOS, null, "ROSA"),
                forbiddenSignature("GET", "/api/v1/customers/by-dni/" + DNI_UNLINKED, null, "ROSA"));
        assertEquals(forbiddenSignature("GET", "/api/v1/progress/" + DNI_CARLOS, null, "ROSA"),
                forbiddenSignature("GET", "/api/v1/progress/" + DNI_UNLINKED, null, "ROSA"));
        assertEquals(forbiddenSignature("GET", "/api/v1/routines/history/" + DNI_CARLOS, null, "ROSA"),
                forbiddenSignature("GET", "/api/v1/routines/history/" + DNI_UNLINKED, null, "ROSA"));
        assertEquals(forbiddenSignature("GET", "/api/v1/routines/by-email/" + CARLOS + "/history", null, "MARCO"),
                forbiddenSignature("GET", "/api/v1/routines/by-email/ghost@x.com/history", null, "MARCO"));
        assertEquals(forbiddenSignature("GET", "/api/v1/attendance/" + DNI_CARLOS, null, "ROSA"),
                forbiddenSignature("GET", "/api/v1/attendance/" + DNI_UNLINKED, null, "ROSA"));
        assertEquals(forbiddenSignature("GET", "/api/v1/identity/" + DNI_CARLOS, null, "ROSA"),
                forbiddenSignature("GET", "/api/v1/identity/" + DNI_UNLINKED, null, "ROSA"));
        assertEquals(forbiddenSignature("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + ROSA + "\"}", "LUCIA"),
                forbiddenSignature("POST", "/api/v1/routines/assign", "{\"customer_email\":\"ghost@x.com\"}", "LUCIA"));
        assertEquals(forbiddenSignature("DELETE", "/api/v1/bookings/" + bLuciaCarlos.getId(), null, "CARLOS"),
                forbiddenSignature("DELETE", "/api/v1/bookings/999999", null, "CARLOS"));
    }

    // =============================== relación entrenador-cliente dinámica ===============================

    @Test
    void trainerAccessFollowsBookings() throws Exception {
        assertEquals(403, call("GET", "/api/v1/progress/by-email/" + CARLOS, null, "MARCO").getResponse().getStatus());
        assertEquals(403, call("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "MARCO").getResponse().getStatus());
        // Marco reserva con Carlos => desde ese momento Carlos es su cliente
        bookings.save(new Booking(customers.findById(CARLOS).orElseThrow(), trainers.findById(MARCO).orElseThrow(),
                new Booking.Schedule(D2, LocalTime.parse("15:00")), null));
        assertEquals(200, call("GET", "/api/v1/progress/by-email/" + CARLOS, null, "MARCO").getResponse().getStatus());
        assertEquals(201, call("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "MARCO").getResponse().getStatus());
        // y deja de serlo al cancelar la única reserva
        bookings.findAll().stream().filter(b -> b.getTrainerEmail().equals(MARCO) && b.getCustomerEmail().equals(CARLOS))
                .forEach(bookings::delete);
        assertEquals(403, call("GET", "/api/v1/progress/by-email/" + CARLOS, null, "MARCO").getResponse().getStatus());
    }

    // =============================== B6: asignar rutina por DNI o correo ===============================

    @Test
    void assignByEmailAndByDniCreateTheRoutineForTheRightCustomer() throws Exception {
        int carlosBefore = ext.routineHistory(CARLOS).size(), diegoBefore = ext.routineHistory(DIEGO).size();
        assertEquals(201, call("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "LUCIA").getResponse().getStatus());
        assertEquals(carlosBefore + 1, ext.routineHistory(CARLOS).size());
        assertEquals(201, call("POST", "/api/v1/routines/assign", "{\"dni\":\"" + DNI_DIEGO + "\"}", "ADMIN").getResponse().getStatus());
        assertEquals(diegoBefore + 1, ext.routineHistory(DIEGO).size());
        assertEquals(carlosBefore + 1, ext.routineHistory(CARLOS).size());
        // rechazado => no se crea
        assertEquals(403, call("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + DIEGO + "\"}", "CARLOS").getResponse().getStatus());
        assertEquals(diegoBefore + 1, ext.routineHistory(DIEGO).size());
    }

    @Test
    void assignResponseKeepsItsShape() throws Exception {
        String json = call("POST", "/api/v1/routines/assign", "{\"customer_email\":\"" + CARLOS + "\"}", "ADMIN")
                .getResponse().getContentAsString();
        java.util.Map<String, Object> plan = JsonPath.read(json, "$.data");
        assertEquals(java.util.Set.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday"), plan.keySet());
    }

    @Test
    void assignValidationReportsTheFieldsInvolved() throws Exception {
        // ambos => 400 con un error en cada campo
        String both = call("POST", "/api/v1/routines/assign",
                "{\"dni\":\"" + DNI_CARLOS + "\",\"customer_email\":\"" + CARLOS + "\"}", "ADMIN").getResponse().getContentAsString();
        List<String> fields = JsonPath.read(both, "$.error.details[*].field");
        assertTrue(fields.containsAll(List.of("dni", "customerEmail")), both);
        // ninguno => 400 con el mismo par de campos
        String none = call("POST", "/api/v1/routines/assign", "{}", "ADMIN").getResponse().getContentAsString();
        assertTrue(((List<String>) JsonPath.read(none, "$.error.details[*].field")).containsAll(List.of("dni", "customerEmail")), none);
        // correo inválido => error solo en customerEmail
        String bad = call("POST", "/api/v1/routines/assign", "{\"customer_email\":\"nope\"}", "ADMIN").getResponse().getContentAsString();
        assertEquals(List.of("customerEmail"), JsonPath.read(bad, "$.error.details[*].field"), bad);
        // la validación (400) no depende del rol: un cliente sin permiso con cuerpo inválido recibe 400 y no 403,
        // porque el 400 no revela nada; con cuerpo válido recibe 403.
        assertEquals(400, call("POST", "/api/v1/routines/assign", "{}", "CARLOS").getResponse().getStatus());
    }

    @Test
    void byEmailReadsReturnTheSameBodyAsTheirDniEquivalents() throws Exception {
        assertEquals(bodyData("/api/v1/routines/history/" + DNI_CARLOS), bodyData("/api/v1/routines/by-email/" + CARLOS + "/history"));
        assertEquals(bodyData("/api/v1/routines/active/" + DNI_CARLOS + "?day=tuesday"),
                bodyData("/api/v1/routines/by-email/" + CARLOS + "/active?day=tuesday"));
        assertEquals(bodyData("/api/v1/progress/" + DNI_CARLOS), bodyData("/api/v1/progress/by-email/" + CARLOS));
    }

    private Object bodyData(String path) throws Exception {
        MvcResult r = call("GET", path, null, "ADMIN");
        assertEquals(200, r.getResponse().getStatus(), path);
        return JsonPath.read(r.getResponse().getContentAsString(), "$.data");
    }

    // =============================== escrituras del cliente sobre sí mismo ===============================

    @Test
    void progressWrittenByTheCustomerIsStoredOnTheirRecord() throws Exception {
        int before = progress.findByCustomerEmailOrderByDateAsc(CARLOS).size();
        String body = "{\"dni\":\"" + DNI_CARLOS + "\",\"weightKg\":70,\"bodyFatPct\":20,\"musclePct\":40}";
        assertEquals(201, call("POST", "/api/v1/progress", body, "CARLOS").getResponse().getStatus());
        assertEquals(before + 1, progress.findByCustomerEmailOrderByDateAsc(CARLOS).size());
        // otro cliente con el DNI de Carlos no escribe nada
        assertEquals(403, call("POST", "/api/v1/progress", body, "ROSA").getResponse().getStatus());
        assertEquals(before + 1, progress.findByCustomerEmailOrderByDateAsc(CARLOS).size());
        assertEquals(0, progress.findByCustomerEmailOrderByDateAsc(ROSA).size());
        // el segundo registro del día sigue dando 409 (regla existente)
        assertEquals(409, call("POST", "/api/v1/progress", body, "CARLOS").getResponse().getStatus());
    }

    @Test
    void attendanceRegisteredByOwnersOnly() throws Exception {
        int before = attendance.findByEmailOrderByTimestampAsc(CARLOS).size();
        assertEquals(200, call("POST", "/api/v1/access", "{\"dni\":\"" + DNI_CARLOS + "\"}", "CARLOS").getResponse().getStatus());
        assertEquals(before + 1, attendance.findByEmailOrderByTimestampAsc(CARLOS).size());
        assertEquals(403, call("POST", "/api/v1/access", "{\"dni\":\"" + DNI_CARLOS + "\"}", "ROSA").getResponse().getStatus());
        assertEquals(before + 1, attendance.findByEmailOrderByTimestampAsc(CARLOS).size());
    }

    @Test
    void identityLinksAreAdminOnlyAndDoNotChangeOnForbidden() throws Exception {
        assertEquals(403, call("POST", "/api/v1/identity/customer",
                "{\"dni\":\"" + DNI_CARLOS + "\",\"email\":\"" + ROSA + "\"}", "CARLOS").getResponse().getStatus());
        assertEquals(CARLOS, links.findById(DNI_CARLOS).orElseThrow().getEmail());
    }
}
