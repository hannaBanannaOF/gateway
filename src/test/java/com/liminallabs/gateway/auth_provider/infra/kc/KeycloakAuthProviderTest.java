package com.liminallabs.gateway.auth_provider.infra.kc;

import static com.liminallabs.gateway.support.Tokens.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.liminallabs.gateway.auth_provider.application.AuthProviderUnavailableException;
import com.liminallabs.gateway.auth_provider.application.InvalidGrantException;
import com.liminallabs.gateway.token.domain.Token;
import com.sun.net.httpserver.HttpServer;

import reactor.test.StepVerifier;

/** Sobe um HTTP server local no lugar do Keycloak para testar o client de verdade. */
class KeycloakAuthProviderTest {

    private static final String TOKEN_PATH = "/realms/LiminalLabs/protocol/openid-connect/token";
    private static final String TOKEN_JSON = """
        {"access_token":"acc","refresh_token":"ref","expires_in":300,"refresh_expires_in":1800,"token_type":"Bearer"}
        """;

    private HttpServer keycloak;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = TOKEN_JSON;
    private volatile long delayMillis = 0;

    private KeycloakAuthProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        keycloak.createContext(TOKEN_PATH, exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sleep(delayMillis);
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        keycloak.start();

        KeycloakProperties props = new KeycloakProperties(
            "http://localhost:8080",
            "http://127.0.0.1:" + keycloak.getAddress().getPort(),
            "LiminalLabs", "questmaster", "secret", Duration.ofMillis(500));
        provider = new KeycloakAuthProvider(props, WebClient.builder(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        keycloak.stop(0);
    }

    @Test
    void authorizationUrlUsesPublicBaseUrlStateAndPkce() {
        String url = provider.authorizationUrl("http://localhost:8081/oauth/callback", "st-1", "chal-1");

        assertThat(url)
            .startsWith("http://localhost:8080/realms/LiminalLabs/protocol/openid-connect/auth?")
            .contains("client_id=questmaster")
            .contains("response_type=code")
            .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8081%2Foauth%2Fcallback")
            .contains("state=st-1")
            .contains("code_challenge=chal-1")
            .contains("code_challenge_method=S256");
    }

    @Test
    void exchangeCodeSendsAuthorizationCodeGrantWithVerifier() {
        Token token = provider.exchangeCode("the-code", "http://localhost:8081/oauth/callback", "verifier-1").block();

        assertThat(token).isEqualTo(new Token("acc", "ref", 300, 1800, NOW));
        assertThat(lastBody.get())
            .contains("grant_type=authorization_code")
            .contains("code=the-code")
            .contains("code_verifier=verifier-1")
            .contains("client_id=questmaster")
            .contains("client_secret=secret");
    }

    @Test
    void refreshSendsRefreshTokenGrant() {
        Token token = provider.refresh("old-refresh").block();

        assertThat(token.accessToken()).isEqualTo("acc");
        assertThat(lastBody.get())
            .contains("grant_type=refresh_token")
            .contains("refresh_token=old-refresh");
    }

    @Test
    void invalidGrantIsReportedAsSuch() {
        respond(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token is not active\"}");

        StepVerifier.create(provider.refresh("revoked"))
            .expectErrorSatisfies(e -> assertThat(e)
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("Token is not active"))
            .verify();
    }

    @Test
    void invalidClientIsAConfigurationProblemNotAnInvalidGrant() {
        respond(401, "{\"error\":\"invalid_client\"}");

        StepVerifier.create(provider.refresh("r"))
            .expectError(AuthProviderUnavailableException.class)
            .verify();
    }

    @Test
    void serverErrorIsUnavailable() {
        respond(503, "<html>down</html>");

        StepVerifier.create(provider.exchangeCode("c", "http://cb", "v"))
            .expectError(AuthProviderUnavailableException.class)
            .verify();
    }

    @Test
    void connectionFailureIsUnavailable() {
        keycloak.stop(0);

        StepVerifier.create(provider.refresh("r"))
            .expectError(AuthProviderUnavailableException.class)
            .verify();
    }

    @Test
    void slowResponseTimesOutAsUnavailable() {
        delayMillis = 2_000;

        StepVerifier.create(provider.refresh("r"))
            .expectError(AuthProviderUnavailableException.class)
            .verify(Duration.ofSeconds(5));
    }

    private void respond(int status, String body) {
        this.status = status;
        this.responseBody = body;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
