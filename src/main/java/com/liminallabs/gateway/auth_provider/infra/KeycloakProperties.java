package com.liminallabs.gateway.auth_provider.infra;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "liminallabs.gateway.auth.keycloak")
@Getter
@Setter
public class KeycloakProperties {
    private String baseUrl;
    private String baseUrlInternal;
    private String realm;
    private String clientId;
    private String clientSecret;
}
