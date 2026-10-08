package com.liminallabs.gateway.token.transport;

import java.time.Clock;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liminallabs.gateway.token.application.SessionService;
import com.liminallabs.gateway.token.domain.Token;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/oauth/bypass")
@ConditionalOnProperty(name = "liminallabs.gateway.security.allow-token-bypass", havingValue = "true")
@RequiredArgsConstructor
public class DevTokenBypassController {

    private final SessionService sessionService;
    private final Clock clock;

    @PostMapping
    public Mono<String> bypassAuth(@RequestBody BypassToken token) {
        return sessionService.create(token.toToken(clock)).map(UUID::toString);
    }

    @PutMapping("/{id}")
    public Mono<Void> bypassAuthUpdate(@RequestBody BypassToken token, @PathVariable UUID id) {
        return sessionService.update(id, token.toToken(clock));
    }

    /** Mesmo formato da resposta de token OAuth, para colar direto o JSON do Keycloak. */
    record BypassToken(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_expires_in") long refreshExpiresIn
    ) {

        Token toToken(Clock clock) {
            return new Token(accessToken, refreshToken, expiresIn, refreshExpiresIn, clock.instant());
        }
    }
}
