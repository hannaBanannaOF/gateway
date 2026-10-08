package com.liminallabs.gateway.token.transport;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import com.liminallabs.gateway.config.GatewayProperties;

import lombok.RequiredArgsConstructor;

/** Leitura e escrita dos cookies de sessão e de login. */
@Component
@RequiredArgsConstructor
class SessionCookies {

    private static final Duration LOGIN_TTL = Duration.ofMinutes(10);

    private final GatewayProperties properties;

    Optional<UUID> sessionId(ServerHttpRequest request) {
        return value(request, properties.sessionCookieName()).flatMap(value -> {
            try {
                return Optional.of(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                // Cookie adulterado/antigo: segue sem Bearer e o serviço responde 401 -> login
                return Optional.empty();
            }
        });
    }

    ResponseCookie session(UUID sessionId) {
        return base(properties.sessionCookieName(), sessionId.toString())
            .sameSite(properties.cookieSameSite())
            .maxAge(properties.cookieMaxAge())
            .build();
    }

    Optional<LoginRequest> login(ServerHttpRequest request) {
        return value(request, loginCookieName()).flatMap(LoginRequest::decode);
    }

    /** Lax e não Strict: o callback chega por navegação vinda do domínio do provider, e Strict não seria enviado. */
    ResponseCookie login(LoginRequest login) {
        return base(loginCookieName(), login.encode())
            .sameSite("Lax")
            .maxAge(LOGIN_TTL)
            .build();
    }

    ResponseCookie clearLogin() {
        return base(loginCookieName(), "")
            .sameSite("Lax")
            .maxAge(Duration.ZERO)
            .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
            .path("/")
            .secure(properties.cookieSecure())
            .httpOnly(true);
        if (properties.hasCookieDomain()) {
            builder.domain(properties.cookieDomain());
        }
        return builder;
    }

    private String loginCookieName() {
        return properties.sessionCookieName() + "_LOGIN";
    }

    private static Optional<String> value(ServerHttpRequest request, String name) {
        return Optional.ofNullable(request.getCookies().getFirst(name)).map(HttpCookie::getValue);
    }
}
