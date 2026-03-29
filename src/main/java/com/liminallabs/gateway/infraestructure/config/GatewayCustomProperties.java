package com.liminallabs.gateway.infraestructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "liminallabs.gateway")
@Getter
@Setter
public class GatewayCustomProperties {

    private String sessionCookieName;
    private String oauthCallbackUrl;
    private String frontendUrl;
    private KeycloakProperties keycloak;
    private Amqp amqp;


    @Getter
    @Setter
    public static class KeycloakProperties {
        private String baseUrl;
        private String baseUrlInternal;
        private String realm;
        private String clientId;
        private String clientSecret;
    }

    @Getter
    @Setter
    public static class Amqp {
        private String queue;
    }

}
