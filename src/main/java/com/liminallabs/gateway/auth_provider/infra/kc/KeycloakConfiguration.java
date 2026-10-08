package com.liminallabs.gateway.auth_provider.infra.kc;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnProperty(name = "liminallabs.gateway.auth.provider-name", havingValue = "keycloak")
@EnableConfigurationProperties(KeycloakProperties.class)
class KeycloakConfiguration {

    @Bean
    KeycloakAuthProvider keycloakAuthProvider(KeycloakProperties props, WebClient.Builder webClientBuilder, Clock clock) {
        return new KeycloakAuthProvider(props, webClientBuilder, clock);
    }
}
