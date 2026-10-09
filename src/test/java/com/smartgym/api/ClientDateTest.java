package com.smartgym.api;

import com.smartgym.domain.IdentityLink;
import com.smartgym.model.Customer;
import com.smartgym.model.Trainer;
import com.smartgym.repository.*;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.smartgym.security.TestJwtDecoderConfig.token;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fecha opcional enviada por el cliente en reservas y progreso (servidor en UTC, cliente en otra zona). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class ClientDateTest {

    static final String LUCIA = "lucia@x.com";
    static final String CARLOS = "carlos@x.com";
    static final String DNI = "11111111";
    static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired MockMvc mvc;
    @Autowired CustomerRepository customers;
    @Autowired TrainerRepository trainers;
    @Autowired BookingRepository bookings;
    @Autowired IdentityLinkRepository links;
    @Autowired ProgressRecordRepository progress;

    @BeforeEach
    void seed() {
        cleanUp();
        trainers.save(new Trainer(LUCIA, "Lucia Paredes", 33, "Fuerza"));
        customers.save(new Customer(CARLOS, "Carlos Mendoza", 28));
        links.save(new IdentityLink(DNI, CARLOS));
    }

    @AfterEach
    void cleanUp() {
        progress.deleteAll();
        bookings.deleteAll();
        links.deleteAll();
        customers.deleteAll();
        trainers.deleteAll();
    }

    private ResultActions book(String date, String time) throws Exception {
        return book(date, time, null);
    }

    private ResultActions book(String date, String time, Integer offset) throws Exception {
        String d = (date == null ? "" : ",\"date\":\"" + date + "\"")
                + (offset == null ? "" : ",\"utcOffsetMinutes\":" + offset);
        return mvc.perform(post("/api/v1/bookings")
                .header("Authorization", "Bearer " + token("cliente", CARLOS))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerEmail\":\"" + CARLOS + "\",\"trainerEmail\":\"" + LUCIA
                        + "\",\"time\":\"" + time + "\"" + d + "}"));
    }

    private ResultActions addProgress(String date) throws Exception {
        return addProgress(date, null);
    }

    private ResultActions addProgress(String date, Integer offset) throws Exception {
        String d = (date == null ? "" : ",\"date\":\"" + date + "\"")
                + (offset == null ? "" : ",\"utcOffsetMinutes\":" + offset);
        return mvc.perform(post("/api/v1/progress")
                .header("Authorization", "Bearer " + token("cliente", CARLOS))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dni\":\"" + DNI + "\",\"weightKg\":70,\"bodyFatPct\":20,\"musclePct\":40" + d + "}"));
    }

    @Test
    void bookingWithClientDateAcceptsTimeEarlierThanUtcNowButWithinTwelveHours() throws Exception {
        LocalDateTime slot = LocalDateTime.now(ZoneOffset.UTC).minusHours(5); // p. ej. Lima: hora de pared aun futura
        book(slot.toLocalDate().toString(), slot.format(HHMM)).andExpect(status().isCreated());
    }

    @Test
    void bookingWithClientDateRejectsTimeOlderThanTwelveHours() throws Exception {
        LocalDateTime slot = LocalDateTime.now(ZoneOffset.UTC).minusHours(13);
        book(slot.toLocalDate().toString(), slot.format(HHMM)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void bookingWithClientDateOutsideOneDayWindowIsRejected() throws Exception {
        LocalDate utc = LocalDate.now(ZoneOffset.UTC);
        book(utc.plusDays(2).toString(), "10:00").andExpect(status().isUnprocessableEntity());
        book(utc.minusDays(2).toString(), "10:00").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void bookingWithTomorrowClientDateIsAccepted() throws Exception {
        book(LocalDate.now(ZoneOffset.UTC).plusDays(1).toString(), "10:00").andExpect(status().isCreated());
    }

    @Test
    void bookingWithoutDateKeepsStrictPastCheck() throws Exception {
        // sin fecha: hoy del servidor (zona local de la JVM); horas dependientes del reloj, se omite lo imposible
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime past = now.minusHours(2);
        if (past.toLocalDate().equals(now.toLocalDate())) {
            book(null, past.format(HHMM)).andExpect(status().isUnprocessableEntity());
        }
        LocalDateTime future = now.plusHours(2);
        if (future.toLocalDate().equals(now.toLocalDate())) {
            book(null, future.format(HHMM)).andExpect(status().isCreated());
        }
        assumeTrue(past.toLocalDate().equals(now.toLocalDate()) || future.toLocalDate().equals(now.toLocalDate()));
    }

    @Test
    void bookingWithInvalidDateFormatIsBadRequest() throws Exception {
        book("08/10/2026", "10:00").andExpect(status().isBadRequest());
    }

    @Test
    void bookingWithImpossibleDateIsRejected() throws Exception {
        book("2026-13-45", "10:00").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void progressWithClientDateIsStoredWithThatDate() throws Exception {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        addProgress(yesterday.toString()).andExpect(status().isCreated());
        var saved = progress.findByCustomerEmailOrderByDateAsc(CARLOS);
        assertEquals(1, saved.size());
        assertEquals(yesterday, saved.get(0).getDate());
    }

    @Test
    void progressSameDayTwiceIsConflict() throws Exception {
        String d = LocalDate.now(ZoneOffset.UTC).toString();
        addProgress(d).andExpect(status().isCreated());
        addProgress(d).andExpect(status().isConflict());
    }

    @Test
    void progressWithDateOutsideWindowIsRejected() throws Exception {
        addProgress(LocalDate.now(ZoneOffset.UTC).plusDays(3).toString()).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void progressWithInvalidDateFormatIsBadRequest() throws Exception {
        addProgress("yesterday").andExpect(status().isBadRequest());
    }

    @Test
    void progressWithoutDateStillWorks() throws Exception {
        addProgress(null).andExpect(status().isCreated());
    }

    // ---- con utcOffsetMinutes (Lima = -300): hoy local estricto ----

    static final int LIMA = -300;

    private LocalDateTime limaNow() {
        return LocalDateTime.now(ZoneOffset.UTC).plusMinutes(LIMA);
    }

    @Test
    void offsetRejectsLocalSlotAlreadyPastThatLenientWindowAccepted() throws Exception {
        LocalDateTime slot = limaNow().minusHours(2);
        assumeTrue(slot.toLocalDate().equals(limaNow().toLocalDate()));
        // sin offset (fallback tolerante) se acepta; con offset se rechaza
        book(slot.toLocalDate().toString(), slot.format(HHMM), LIMA).andExpect(status().isUnprocessableEntity());
        book(slot.toLocalDate().toString(), slot.format(HHMM)).andExpect(status().isCreated());
    }

    @Test
    void offsetAcceptsFutureLocalSlot() throws Exception {
        LocalDateTime slot = limaNow().plusHours(2);
        assumeTrue(slot.toLocalDate().equals(limaNow().toLocalDate()));
        book(slot.toLocalDate().toString(), slot.format(HHMM), LIMA).andExpect(status().isCreated());
    }

    @Test
    void offsetRejectsDateThatIsNotClientLocalToday() throws Exception {
        LocalDate today = limaNow().toLocalDate();
        book(today.plusDays(1).toString(), "10:00", LIMA).andExpect(status().isUnprocessableEntity());
        book(today.minusDays(1).toString(), "10:00", LIMA).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void offsetOutOfRangeIsBadRequest() throws Exception {
        String d = LocalDate.now(ZoneOffset.UTC).toString();
        book(d, "10:00", 900).andExpect(status().isBadRequest());
        book(d, "10:00", -800).andExpect(status().isBadRequest());
        addProgress(d, 900).andExpect(status().isBadRequest());
    }

    @Test
    void progressAcceptsSnakeCaseOffsetAlias() throws Exception {
        String d = LocalDate.now(ZoneOffset.UTC).toString();
        mvc.perform(post("/api/v1/progress")
                        .header("Authorization", "Bearer " + token("cliente", CARLOS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"" + DNI + "\",\"weightKg\":70,\"bodyFatPct\":20,\"musclePct\":40,"
                                + "\"date\":\"" + d + "\",\"utc_offset_minutes\":900}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void progressWithOffsetStoresClientLocalToday() throws Exception {
        LocalDate today = limaNow().toLocalDate();
        addProgress(today.toString(), LIMA).andExpect(status().isCreated());
        assertEquals(today, progress.findByCustomerEmailOrderByDateAsc(CARLOS).get(0).getDate());
    }

    @Test
    void progressWithOffsetRejectsNonLocalToday() throws Exception {
        addProgress(limaNow().toLocalDate().plusDays(1).toString(), LIMA).andExpect(status().isUnprocessableEntity());
    }
}
