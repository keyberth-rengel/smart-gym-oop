package com.smartgym.clerk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente mínimo de la API de backend de Clerk para dar acceso con un rol a un correo:
 * envía una invitación y, si ese correo ya tiene cuenta, le asigna el rol en su {@code public_metadata}.
 * Nunca lanza excepciones por fallos de Clerk (devuelve {@link InvitationResult#failed}) y nunca registra la clave secreta.
 */
public class ClerkClient {

    private static final Logger log = LoggerFactory.getLogger(ClerkClient.class);

    private final RestClient rest;
    private final String secretKey;
    private final String redirectUrl;
    private final ObjectMapper mapper;

    public ClerkClient(RestClient rest, String secretKey, String redirectUrl, ObjectMapper mapper) {
        this.rest = rest;
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.redirectUrl = redirectUrl == null ? "" : redirectUrl.trim();
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return !secretKey.isEmpty();
    }

    /** Invita a {@code email} con el rol indicado; si ya tiene cuenta, le asigna el rol. */
    public InvitationResult invite(String email, String role) {
        if (!isConfigured()) {
            return InvitationResult.skipped();
        }
        try {
            sendInvitation(email, role);
            return InvitationResult.invited();
        } catch (RestClientResponseException ex) {
            if (isAlreadyExists(ex)) {
                return assignRoleToExistingUser(email, role);
            }
            log.warn("Clerk rejected the invitation (role={}, http={})", role, ex.getStatusCode().value());
            return InvitationResult.failed("clerk_error_" + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            log.warn("Clerk invitation failed (role={}): {}", role, ex.getClass().getSimpleName());
            return InvitationResult.failed("clerk_unreachable");
        }
    }

    private void sendInvitation(String email, String role) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email_address", email);
        body.put("public_metadata", Map.of("role", role));
        if (!redirectUrl.isEmpty()) {
            body.put("redirect_url", redirectUrl);
        }
        body.put("notify", true);
        body.put("ignore_existing", false);
        rest.post().uri("/v1/invitations")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private InvitationResult assignRoleToExistingUser(String email, String role) {
        try {
            String json = rest.get()
                    .uri(u -> u.path("/v1/users").queryParam("email_address[]", "{email}").build(email))
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .retrieve()
                    .body(String.class);
            JsonNode users = mapper.readTree(json == null ? "[]" : json);
            if (!users.isArray() || users.isEmpty()) {
                return InvitationResult.failed("clerk_user_not_found");
            }
            JsonNode user = users.get(0);
            String id = user.path("id").asText("");
            if (id.isEmpty()) {
                return InvitationResult.failed("clerk_user_not_found");
            }
            // No degradar por accidente a un administrador que ya tiene cuenta con ese correo.
            if ("admin".equalsIgnoreCase(user.path("public_metadata").path("role").asText(""))) {
                return InvitationResult.failed("clerk_existing_admin");
            }
            rest.patch().uri("/v1/users/{id}/metadata", id)
                    .header(HttpHeaders.AUTHORIZATION, bearer())
                    .body(Map.of("public_metadata", Map.of("role", role)))
                    .retrieve()
                    .toBodilessEntity();
            return InvitationResult.roleUpdated();
        } catch (RestClientResponseException ex) {
            log.warn("Clerk rejected the role update (role={}, http={})", role, ex.getStatusCode().value());
            return InvitationResult.failed("clerk_error_" + ex.getStatusCode().value());
        } catch (Exception ex) {
            log.warn("Clerk role update failed (role={}): {}", role, ex.getClass().getSimpleName());
            return InvitationResult.failed("clerk_unreachable");
        }
    }

    private static boolean isAlreadyExists(RestClientResponseException ex) {
        return ex.getStatusCode().value() == 422 && ex.getResponseBodyAsString().contains("form_identifier_exists");
    }

    private String bearer() {
        return "Bearer " + secretKey;
    }
}
