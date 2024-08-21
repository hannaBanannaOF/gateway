package com.hbsites.gateway.infraestructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "hbsites.gateway")
@Getter
@Setter
public class GatewayCustomProperties {

    private String sessionCookieName;
    private String oauthCallbackUrl;
    private String frontendUrl;
    private KeycloakProperties keycloak;


    @Getter
    @Setter
    public static class KeycloakProperties {
        private String BaseUrl;
        private String redirectUrl;
        private String realm;
        private String clientId;
        private String clientSecret;
    }

}
