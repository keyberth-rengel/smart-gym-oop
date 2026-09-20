package com.smartgym.api;

import com.smartgym.repository.*;
import com.smartgym.security.TestJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.smartgym.security.TestJwtDecoderConfig.token;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin CLERK_SECRET_KEY (configuración por defecto) el alta funciona y la invitación se omite sin tocar la red. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class TrainerInvitationDefaultConfigTest {

    @Autowired MockMvc mvc;
    @Autowired TrainerRepository trainers;

    @Test
    void trainerIsCreatedAndInvitationIsSkippedWhenClerkIsNotConfigured() throws Exception {
        trainers.deleteById("skip@x.com");
        mvc.perform(post("/api/v1/trainers")
                        .header("Authorization", "Bearer " + token("admin", "admin@x.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"skip@x.com\",\"name\":\"Skip\",\"age\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invitation.status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.invitation.message").value("clerk_not_configured"));

        mvc.perform(post("/api/v1/trainers/skip@x.com/invite")
                        .header("Authorization", "Bearer " + token("admin", "admin@x.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SKIPPED"));
        trainers.deleteById("skip@x.com");
    }
}
