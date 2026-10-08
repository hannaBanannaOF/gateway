package com.liminallabs.gateway.token.domain;

import java.time.Instant;

/**
 * Conjunto de tokens OAuth de uma sessão.
 *
 * @param expiresIn validade do access token em segundos, contada a partir de {@code issuedAt} (0 = sem prazo informado)
 * @param refreshExpiresIn validade do refresh token em segundos (0 = sem prazo informado)
 * @param issuedAt quando o provider emitiu os tokens
 */
public record Token(
    String accessToken,
    String refreshToken,
    long expiresIn,
    long refreshExpiresIn,
    Instant issuedAt
) {

    public boolean isAccessTokenValid(Instant now) {
        return issuedAt.plusSeconds(expiresIn).isAfter(now);
    }

    public boolean isRefreshTokenValid(Instant now) {
        return issuedAt.plusSeconds(refreshExpiresIn).isAfter(now);
    }

    /** Sessão morta: havia prazo e nem o access nem o refresh token valem mais. */
    public boolean isExpired(Instant now) {
        return hasLifetime() && !isAccessTokenValid(now) && !isRefreshTokenValid(now);
    }

    /**
     * A sessão vive enquanto o refresh token for válido (ou o access token, se durar mais).
     * Sem nenhum prazo informado pelo provider (ex.: offline token do Keycloak), não expira e devolve {@code null}.
     */
    public Instant sessionExpiresAt() {
        return hasLifetime() ? issuedAt.plusSeconds(Math.max(expiresIn, refreshExpiresIn)) : null;
    }

    /** Alguns providers não devolvem refresh token novo no refresh: mantém o anterior. */
    public Token withRefreshTokenFallback(String previousRefreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            return this;
        }
        return new Token(accessToken, previousRefreshToken, expiresIn, refreshExpiresIn, issuedAt);
    }

    private boolean hasLifetime() {
        return expiresIn > 0 || refreshExpiresIn > 0;
    }
}
