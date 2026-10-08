package com.liminallabs.gateway.token.transport;

import static com.liminallabs.gateway.support.TestProperties.COOKIE;
import static com.liminallabs.gateway.support.Tokens.fresh;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liminallabs.gateway.auth_provider.application.AuthProviderUnavailableException;
import com.liminallabs.gateway.config.GatewayProperties;
import com.liminallabs.gateway.support.TestProperties;
import com.liminallabs.gateway.token.application.SessionService;

import reactor.core.publisher.Mono;

class CustomBearerAuthFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionService sessionService = mock(SessionService.class);
    private CustomBearerAuthFilter filter;

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        GatewayProperties properties = TestProperties.gateway();
        filter = new CustomBearerAuthFilter(properties, new SessionCookies(properties), sessionService, objectMapper);
    }

    @Test
    void validSessionInjectsBearerAndStripsCookie() {
        UUID id = UUID.randomUUID();
        when(sessionService.resolve(id)).thenReturn(Mono.just(fresh("access-1")));

        run(withSession(id.toString()), HttpStatus.OK);

        HttpHeaders headers = forwardedHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-1");
        assertThat(headers.containsKey(HttpHeaders.COOKIE)).isFalse();
    }

    @Test
    void endedSessionIsForwardedWithoutAuthorization() {
        // Sessão desconhecida, expirada ou com refresh rejeitado: nunca "Bearer " vazio
        UUID id = UUID.randomUUID();
        when(sessionService.resolve(id)).thenReturn(Mono.empty());

        run(withSession(id.toString()), HttpStatus.OK);

        assertThat(forwardedHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void requestWithoutCookieIsForwardedUntouched() {
        run(MockServerHttpRequest.get("/core/api/v1/campaigns"), HttpStatus.OK);

        assertThat(forwardedHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        verifyNoInteractions(sessionService);
    }

    @Test
    void malformedCookieDoesNotBreakRequest() {
        MockServerWebExchange exchange = run(withSession("not-a-uuid"), HttpStatus.OK);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(forwardedHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        verifyNoInteractions(sessionService);
    }

    @Test
    void providerOutageAnswers503WithoutCallingUpstream() {
        when(sessionService.resolve(any())).thenReturn(Mono.error(new AuthProviderUnavailableException("down", null)));

        MockServerWebExchange exchange = run(withSession(UUID.randomUUID().toString()), HttpStatus.OK);

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void downstream401BecomesLoginRedirectWithReturnTo() throws Exception {
        MockServerWebExchange exchange = run(
            MockServerHttpRequest.get("/core/api/v1/campaigns").header("Original-Url", "/campaigns?page=2&q=a b"),
            HttpStatus.UNAUTHORIZED
        );

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(redirectUrl(exchange))
            .isEqualTo("http://localhost:8081/oauth/login?returnTo=%2Fcampaigns%3Fpage%3D2%26q%3Da%20b");
    }

    @Test
    void unsafeOriginalUrlIsNotPropagated() throws Exception {
        MockServerWebExchange exchange = run(
            MockServerHttpRequest.get("/core/api/v1/campaigns").header("Original-Url", "//evil.com"),
            HttpStatus.UNAUTHORIZED
        );

        assertThat(redirectUrl(exchange)).isEqualTo("http://localhost:8081/oauth/login");
    }

    @Test
    void successfulResponseIsNotRewritten() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/core/api/v1/campaigns"), HttpStatus.OK);

        assertThat(exchange.getResponse().getHeaders().getContentType()).isNull();
    }

    private String redirectUrl(MockServerWebExchange exchange) throws Exception {
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        return body.get("redirectUrl").asText();
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request, HttpStatus downstreamStatus) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex);
            ex.getResponse().setStatusCode(downstreamStatus);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        return exchange;
    }

    private HttpHeaders forwardedHeaders() {
        return forwarded.get().getRequest().getHeaders();
    }

    private static MockServerHttpRequest.BaseBuilder<?> withSession(String value) {
        return MockServerHttpRequest.get("/core/api/v1/campaigns")
            .cookie(new HttpCookie(COOKIE, value))
            .header(HttpHeaders.COOKIE, COOKIE + "=" + value);
    }
}
