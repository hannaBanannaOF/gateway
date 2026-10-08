package com.liminallabs.gateway.support;

import com.liminallabs.gateway.config.GatewayProperties;

public final class TestProperties {

    public static final String COOKIE = "QUESTMASTER_SESSION";
    public static final String LOGIN_COOKIE = COOKIE + "_LOGIN";
    public static final String CALLBACK = "http://localhost:8081/oauth/callback";
    public static final String FRONTEND = "http://localhost:3000";

    private TestProperties() {
    }

    public static GatewayProperties gateway() {
        return gateway("localhost");
    }

    public static GatewayProperties gateway(String cookieDomain) {
        return new GatewayProperties(COOKIE, CALLBACK, null, FRONTEND, cookieDomain, "Strict", false, 3600,
            new GatewayProperties.Auth("keycloak"));
    }
}
