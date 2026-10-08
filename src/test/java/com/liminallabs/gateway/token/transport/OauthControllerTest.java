package com.liminallabs.gateway.token.transport;

import static com.liminallabs.gateway.support.TestProperties.CALLBACK;
import static com.liminallabs.gateway.support.TestProperties.COOKIE;
import static com.liminallabs.gateway.support.TestProperties.FRONTEND;
import static com.liminallabs.gateway.support.TestProperties.LOGIN_COOKIE;
import static com.liminallabs.gateway.support.Tokens.fresh;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.auth_provider.application.AuthProviderUnavailableException;
import com.liminallabs.gateway.auth_provider.application.InvalidGrantException;
import com.liminallabs.gateway.config.GatewayProperties;
import com.liminallabs.gateway.support.TestProperties;
import com.liminallabs.gateway.token.application.SessionService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OauthControllerTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final AuthProvider authProvider = mock(AuthProvider.class);
    private OauthController controller;

    @BeforeEach
    void setUp() {
        controller = controller(TestProperties.gateway());
    }

    @Test
    void loginSetsLoginCookieAndRedirectsWithStateAndPkce() {
        when(authProvider.authorizationUrl(eq(CALLBACK), anyString(), anyString()))
            .thenAnswer(inv -> "http://kc/auth?state=" + inv.getArgument(1) + "&code_challenge=" + inv.getArgument(2));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/oauth/login"));

        controller.login("/campaigns", exchange).block();

        ResponseCookie cookie = exchange.getResponse().getCookies().getFirst(LOGIN_COOKIE);
        LoginRequest login = LoginRequest.decode(cookie.getValue()).orElseThrow();
        assertThat(login.returnTo()).isEqualTo("/campaigns");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(10));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation())
            .hasToString("http://kc/auth?state=" + login.state() + "&code_challenge=" + login.pkce().challenge());
    }

    @Test
    void callbackExchangesCodeWithVerifierCreatesSessionAndRedirects() {
        LoginRequest login = LoginRequest.start("/campaigns/42");
        UUID sessionId = UUID.randomUUID();
        when(authProvider.exchangeCode("abc", CALLBACK, login.pkce().verifier())).thenReturn(Mono.just(fresh("a")));
        when(sessionService.create(fresh("a"))).thenReturn(Mono.just(sessionId));

        MockServerWebExchange exchange = callback("abc", login.state(), login);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString(FRONTEND + "/campaigns/42");

        ResponseCookie session = exchange.getResponse().getCookies().getFirst(COOKIE);
        assertThat(session.getValue()).isEqualTo(sessionId.toString());
        assertThat(session.isHttpOnly()).isTrue();
        assertThat(session.getSameSite()).isEqualTo("Strict");
        assertThat(session.getPath()).isEqualTo("/");
        assertThat(session.getMaxAge()).isEqualTo(Duration.ofSeconds(3600));
        assertThat(session.getDomain()).isEqualTo("localhost");

        assertThat(exchange.getResponse().getCookies().getFirst(LOGIN_COOKIE).getMaxAge()).isZero();
    }

    @Test
    void withoutReturnToRedirectsToFrontendRoot() {
        LoginRequest login = LoginRequest.start(null);
        stubLogin(login);

        MockServerWebExchange exchange = callback("abc", login.state(), login);

        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString(FRONTEND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"@evil.com", "//evil.com", "/\\evil.com", "http://localhost:3000/campaigns", "campaigns"})
    void returnToThatIsNotARelativePathFallsBackToFrontendRoot(String returnTo) {
        LoginRequest login = LoginRequest.start(returnTo);
        stubLogin(login);

        MockServerWebExchange exchange = callback("abc", login.state(), login);

        assertThat(exchange.getResponse().getHeaders().getLocation()).hasToString(FRONTEND);
    }

    @Test
    void blankCookieDomainIsOmitted() {
        controller = controller(TestProperties.gateway(""));
        LoginRequest login = LoginRequest.start(null);
        stubLogin(login);

        MockServerWebExchange exchange = callback("abc", login.state(), login);

        assertThat(exchange.getResponse().getCookies().getFirst(COOKIE).getDomain()).isNull();
    }

    @Test
    void stateMismatchIsRejectedWithoutTalkingToProvider() {
        LoginRequest login = LoginRequest.start(null);

        expectStatus(controller.callback("abc", "forged", exchange(login)), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(authProvider, sessionService);
    }

    @Test
    void missingLoginCookieIsRejected() {
        expectStatus(controller.callback("abc", "any", exchange(null)), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(authProvider, sessionService);
    }

    @Test
    void missingCodeIsRejected() {
        LoginRequest login = LoginRequest.start(null);

        expectStatus(controller.callback(null, login.state(), exchange(login)), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(authProvider, sessionService);
    }

    @Test
    void rejectedCodeIs400() {
        LoginRequest login = LoginRequest.start(null);
        when(authProvider.exchangeCode(any(), any(), any())).thenReturn(Mono.error(new InvalidGrantException("used")));

        expectStatus(controller.callback("abc", login.state(), exchange(login)), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(sessionService);
    }

    @Test
    void providerOutageIs503() {
        LoginRequest login = LoginRequest.start(null);
        when(authProvider.exchangeCode(any(), any(), any()))
            .thenReturn(Mono.error(new AuthProviderUnavailableException("down", null)));

        expectStatus(controller.callback("abc", login.state(), exchange(login)), HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(sessionService);
    }

    private OauthController controller(GatewayProperties properties) {
        return new OauthController(properties, new SessionCookies(properties), sessionService, authProvider);
    }

    private void stubLogin(LoginRequest login) {
        when(authProvider.exchangeCode("abc", CALLBACK, login.pkce().verifier())).thenReturn(Mono.just(fresh("a")));
        when(sessionService.create(any())).thenReturn(Mono.just(UUID.randomUUID()));
    }

    private MockServerWebExchange callback(String code, String state, LoginRequest login) {
        MockServerWebExchange exchange = exchange(login);
        controller.callback(code, state, exchange).block();
        return exchange;
    }

    private static MockServerWebExchange exchange(LoginRequest login) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/oauth/callback");
        if (login != null) {
            request.cookie(new HttpCookie(LOGIN_COOKIE, login.encode()));
        }
        return MockServerWebExchange.from(request);
    }

    private static void expectStatus(Mono<Void> result, HttpStatus status) {
        StepVerifier.create(result)
            .expectErrorSatisfies(e -> assertThat(e)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    rse -> assertThat(rse.getStatusCode()).isEqualTo(status)))
            .verify();
    }
}
