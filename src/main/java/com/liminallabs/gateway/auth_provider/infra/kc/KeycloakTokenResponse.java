package com.liminallabs.gateway.auth_provider.infra.kc;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liminallabs.gateway.token.domain.Token;

/** Resposta do endpoint de token do Keycloak (formato OAuth, snake_case). */
record KeycloakTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("refresh_expires_in") long refreshExpiresIn
) {

    Token toToken(Instant issuedAt) {
        return new Token(accessToken, refreshToken, expiresIn, refreshExpiresIn, issuedAt);
    }
}
