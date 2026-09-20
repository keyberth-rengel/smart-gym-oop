package com.smartgym.clerk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** ClerkClient contra un servidor simulado: nunca se llama a Clerk real ni se usa una clave real. */
@ExtendWith(OutputCaptureExtension.class)
class ClerkClientTest {

    static final String BASE = "https://clerk.test";
    static final String SECRET = "sk_test_FAKE_SECRET_DO_NOT_LEAK";
    static final String REDIRECT = "http://localhost:4200/auth/sign-up";
    static final String EXISTS = "{\"errors\":[{\"code\":\"form_identifier_exists\",\"message\":\"That email address is taken.\"}]}";

    RestClient.Builder builder;
    MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    ClerkClient client(String secret, String redirect) {
        return new ClerkClient(builder.build(), secret, redirect, new ObjectMapper());
    }

    @Test
    void invitationSentWithExactRequest() {
        server.expect(requestTo(BASE + "/v1/invitations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email_address").value("lucia@x.com"))
                .andExpect(jsonPath("$.public_metadata.role").value("entrenador"))
                .andExpect(jsonPath("$.redirect_url").value(REDIRECT))
                .andExpect(jsonPath("$.notify").value(true))
                .andExpect(jsonPath("$.ignore_existing").value(false))
                .andRespond(withSuccess("{\"id\":\"inv_1\",\"object\":\"invitation\"}", MediaType.APPLICATION_JSON));

        var result = client(SECRET, REDIRECT).invite("lucia@x.com", "entrenador");

        assertThat(result).isEqualTo(new InvitationResult(InvitationResult.Status.INVITED, "invitation_sent"));
        server.verify();
    }

    @Test
    void redirectUrlIsOmittedWhenBlank() {
        server.expect(requestTo(BASE + "/v1/invitations"))
                .andExpect(jsonPath("$.redirect_url").doesNotExist())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client(SECRET, "  ").invite("lucia@x.com", "entrenador").status())
                .isEqualTo(InvitationResult.Status.INVITED);
        server.verify();
    }

    @Test
    void withoutSecretKeyNothingIsSentAndResultIsSkipped() {
        // Sin expectativas registradas: cualquier petición HTTP haría fallar la prueba.
        for (String blank : new String[]{"", "   ", null}) {
            var result = client(blank, REDIRECT).invite("lucia@x.com", "entrenador");
            assertThat(result).isEqualTo(new InvitationResult(InvitationResult.Status.SKIPPED, "clerk_not_configured"));
        }
        assertThat(client("", REDIRECT).isConfigured()).isFalse();
        server.verify();
    }

    @Test
    void existingAccountGetsTheRoleInsteadOfAnInvitation() {
        server.expect(requestTo(BASE + "/v1/invitations")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON).body(EXISTS));
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(containsString(BASE + "/v1/users?")))
                .andExpect(request -> assertThat(java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8))
                        .isEqualTo("email_address[]=lucia@x.com"))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andRespond(withSuccess("[{\"id\":\"user_123\",\"public_metadata\":{}}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v1/users/user_123/metadata")).andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer " + SECRET))
                .andExpect(jsonPath("$.public_metadata.role").value("entrenador"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        var result = client(SECRET, REDIRECT).invite("lucia@x.com", "entrenador");

        assertThat(result).isEqualTo(new InvitationResult(InvitationResult.Status.ROLE_UPDATED, "role_updated_existing_account"));
        server.verify();
    }

    @Test
    void plusSignInTheEmailIsEncodedInTheLookup() {
        server.expect(requestTo(BASE + "/v1/invitations"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(EXISTS));
        server.expect(method(HttpMethod.GET))
                .andExpect(request -> assertThat(request.getURI().getRawQuery()).contains("%2B").doesNotContain("+"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var result = client(SECRET, REDIRECT).invite("sg+clerk_test@example.com", "entrenador");

        assertThat(result.message()).isEqualTo("clerk_user_not_found");
        server.verify();
    }

    @Test
    void existingAccountNotFoundOnLookupFails() {
        server.expect(requestTo(BASE + "/v1/invitations")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(EXISTS));
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client(SECRET, REDIRECT).invite("lucia@x.com", "entrenador"))
                .isEqualTo(new InvitationResult(InvitationResult.Status.FAILED, "clerk_user_not_found"));
        server.verify();
    }

    @Test
    void anExistingAdminIsNeverDowngraded() {
        server.expect(requestTo(BASE + "/v1/invitations")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(EXISTS));
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"user_9\",\"public_metadata\":{\"role\":\"admin\"}}]", MediaType.APPLICATION_JSON));
        // Sin expectativa de PATCH: si se intentara, la prueba fallaría.

        assertThat(client(SECRET, REDIRECT).invite("boss@x.com", "entrenador"))
                .isEqualTo(new InvitationResult(InvitationResult.Status.FAILED, "clerk_existing_admin"));
        server.verify();
    }

    @Test
    void failedRoleUpdateIsReportedAsFailed() {
        server.expect(requestTo(BASE + "/v1/invitations")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(EXISTS));
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("[{\"id\":\"user_1\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v1/users/user_1/metadata")).andRespond(withServerError());

        assertThat(client(SECRET, REDIRECT).invite("lucia@x.com", "entrenador"))
                .isEqualTo(new InvitationResult(InvitationResult.Status.FAILED, "clerk_error_500"));
        server.verify();
    }

    @Test
    void malformedUserLookupBodyFailsWithoutThrowing() {
        server.expect(requestTo(BASE + "/v1/invitations")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(EXISTS));
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("<html>not json", MediaType.TEXT_HTML));

        assertThat(client(SECRET, REDIRECT).invite("lucia@x.com", "entrenador").status())
                .isEqualTo(InvitationResult.Status.FAILED);
    }

    @Test
    void clerkServerErrorIsFailedNotAnException(CapturedOutput output) {
        server.expect(requestTo(BASE + "/v1/invitations")).andRespond(withServerError().body("{\"detail\":\"boom " + SECRET + "\"}"));

        var result = client(SECRET, REDIRECT).invite("lucia@x.com", "entrenador");

        assertThat(result).isEqualTo(new InvitationResult(InvitationResult.Status.FAILED, "clerk_error_500"));
        assertThat(output.getAll()).doesNotContain(SECRET);
        assertThat(result.toString()).doesNotContain(SECRET);
    }

    @Test
    void invalidKeyOrOtherClientErrorsAreFailed(CapturedOutput output) {
        server.expect(requestTo(BASE + "/v1/invitations")).andRespond(withUnauthorizedRequest().body("{\"errors\":[{\"code\":\"authorization_invalid\"}]}"));
        assertThat(client(SECRET, REDIRECT).invite("a@x.com", "entrenador").message()).isEqualTo("clerk_error_401");

        // 422 que NO es "ya existe" (por ejemplo, correo inválido) no dispara la búsqueda de usuario.
        server.reset();
        server.expect(requestTo(BASE + "/v1/invitations"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("{\"errors\":[{\"code\":\"form_param_format_invalid\"}]}"));
        assertThat(client(SECRET, REDIRECT).invite("a@x.com", "entrenador").message()).isEqualTo("clerk_error_422");
        server.verify();
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test
    void networkTimeoutIsFailedNotAnException(CapturedOutput output) {
        server.expect(requestTo(BASE + "/v1/invitations")).andRespond(withException(new SocketTimeoutException("Read timed out")));

        var result = client(SECRET, REDIRECT).invite("lucia@x.com", "entrenador");

        assertThat(result).isEqualTo(new InvitationResult(InvitationResult.Status.FAILED, "clerk_unreachable"));
        assertThat(output.getAll()).doesNotContain(SECRET);
    }
}
