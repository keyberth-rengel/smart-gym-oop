package com.smartgym.clerk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Configura el cliente de la API de backend de Clerk (clave por CLERK_SECRET_KEY; vacía = invitaciones desactivadas). */
@Configuration
public class ClerkConfig {

    @Bean
    ClerkClient clerkClient(RestClient.Builder builder,
                            ObjectMapper mapper,
                            @Value("${clerk.secret-key:}") String secretKey,
                            @Value("${clerk.api-base:https://api.clerk.com}") String apiBase,
                            @Value("${clerk.invitation-redirect-url:}") String redirectUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient rest = builder.baseUrl(apiBase).requestFactory(factory).build();
        return new ClerkClient(rest, secretKey, redirectUrl, mapper);
    }
}
