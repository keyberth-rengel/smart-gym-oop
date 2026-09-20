package com.smartgym.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.smartgym.security.TestJwtDecoderConfig.token;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
class SecurityIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401InApiResponseFormat() throws Exception {
        mvc.perform(get("/api/v1/customers/someone@example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/customers/someone@example.com"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void garbageTokenReturns401() throws Exception {
        mvc.perform(get("/api/v1/customers/someone@example.com").header("Authorization", "Bearer basura"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void nonBearerSchemeReturns401() throws Exception {
        mvc.perform(get("/api/v1/customers/someone@example.com").header("Authorization", "Basic YTpi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everyRoleReachesTheController() throws Exception {
        // El usuario consulta su propio correo (que no existe como cliente): admin, cliente, sin claim y rol desconocido
        // pasan la regla de acceso y llegan al controlador (422 del backend, no 401/403). Un entrenador sin reservas con
        // ese cliente recibe 403 por regla de negocio, no por falta de token.
        for (String role : new String[]{"cliente", "admin", null, "desconocido"}) {
            mvc.perform(get("/api/v1/customers/nobody@example.com")
                            .header("Authorization", "Bearer " + token(role, "nobody@example.com")))
                    .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(get("/api/v1/customers/nobody@example.com")
                        .header("Authorization", "Bearer " + token("entrenador", "coach@example.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRouteWithTokenIs404AndWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/nope").header("Authorization", "Bearer " + token("admin", "a@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/nope")).andExpect(status().isUnauthorized());
    }

    @Test
    void preflightFromAllowedOriginIsAccepted() throws Exception {
        mvc.perform(options("/api/v1/customers")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void preflightFromOtherOriginIsRejected() throws Exception {
        mvc.perform(options("/api/v1/customers")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void actualRequestFromAllowedOriginCarriesCorsHeader() throws Exception {
        mvc.perform(get("/api/v1/health").header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
