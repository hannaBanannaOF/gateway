package com.liminallabs.gateway.properties.domain;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "liminallabs.gateway")
@Getter
@Setter
public class GatewayCustomProperties {

    @NonNull
    private String sessionCookieName;
    private String oauthCallbackUrl;
    private String frontendUrl;
    private String cookieDomain = "localhost";
    private String cookieSameSite = "Strict";
    private boolean cookieSecure = false;
    private long cookieMaxAge = 3600;
    private AuthProperties auth;

    @Getter
    @Setter
    public static class AuthProperties {
        @NonNull
        private String providerName;
    }
}
