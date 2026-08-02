package com.liminallabs.gateway.token.transport;

import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.properties.domain.GatewayCustomProperties;
import com.liminallabs.gateway.token.application.TokenStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.UUID;

@RestController
public class OauthCallbackController {

    @Autowired
    private GatewayCustomProperties properties;

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private AuthProvider authProvider;

    @GetMapping("/oauth/callback")
    public Mono<Void> auth(ServerWebExchange exchange) {
        String code = exchange.getRequest().getQueryParams().getFirst("code");

        if (code == null) {
            return Mono.error(new IllegalArgumentException("Authorization code is missing"));
        }

        // Troca o código pelo token usando o provider configurado
        return Mono.fromCallable(() -> {
            return authProvider.exchangeCodeForToken(code, properties.getOauthCallbackUrl());
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(token -> {
            // Monta a URL de redirecionamento
            String redirectUri = buildRedirectUri(
                    exchange.getRequest().getQueryParams().getFirst("state"));

            // Cria a sessão e o cookie
            UUID sessionTicket = tokenStore.createTokenEntry(token);

            ResponseCookie sessionCookie = buildSessionCookie(sessionTicket.toString());
            exchange.getResponse().addCookie(sessionCookie);
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(URI.create(redirectUri));

            return exchange.getResponse().setComplete();
        });
    }

    private String buildRedirectUri(String state) {
        String redirectUri = properties.getFrontendUrl();
        if (state != null && !state.isBlank()) {
            redirectUri = redirectUri + state;
        }
        return redirectUri;
    }

    private ResponseCookie buildSessionCookie(String sessionId) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(properties.getSessionCookieName(), sessionId)
                .path("/")
                .sameSite(properties.getCookieSameSite())
                .secure(properties.isCookieSecure())
                .httpOnly(true)
                .maxAge(properties.getCookieMaxAge());

        if (properties.getCookieDomain() != null && !properties.getCookieDomain().isBlank()) {
            builder.domain(properties.getCookieDomain());
        }

        return builder.build();
    }
}
