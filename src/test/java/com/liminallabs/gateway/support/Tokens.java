package com.liminallabs.gateway.support;

import java.time.Instant;

import com.liminallabs.gateway.token.domain.Token;

/** Fábrica de tokens para os testes. */
public final class Tokens {

    public static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    private Tokens() {
    }

    /** Token emitido há {@code ageSeconds} segundos em relação a {@link #NOW}. */
    public static Token token(String access, String refresh, long expiresIn, long refreshExpiresIn, long ageSeconds) {
        return new Token(access, refresh, expiresIn, refreshExpiresIn, NOW.minusSeconds(ageSeconds));
    }

    public static Token fresh(String access) {
        return token(access, "refresh", 300, 1800, 0);
    }

    /** Access token expirado, refresh token ainda válido. */
    public static Token accessExpired(String access, String refresh) {
        return token(access, refresh, 300, 1800, 600);
    }
}
