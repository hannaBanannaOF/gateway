package com.liminallabs.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayPropertiesTest {

    @Test
    void loginUrlIsDerivedFromCallbackKeepingPathPrefix() {
        assertThat(props("http://gw/oauth/callback", null).resolvedLoginUrl()).isEqualTo("http://gw/oauth/login");
        assertThat(props("https://x.io/gateway/oauth/callback", " ").resolvedLoginUrl())
            .isEqualTo("https://x.io/gateway/oauth/login");
    }

    @Test
    void explicitLoginUrlWins() {
        assertThat(props("http://gw/oauth/callback", "https://login.x.io/start").resolvedLoginUrl())
            .isEqualTo("https://login.x.io/start");
    }

    private static GatewayProperties props(String callback, String loginUrl) {
        return new GatewayProperties("S", callback, loginUrl, "http://front", null, "Strict", false, 3600,
            new GatewayProperties.Auth("keycloak"));
    }
}
