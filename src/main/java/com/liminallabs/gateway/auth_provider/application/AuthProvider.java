package com.liminallabs.gateway.auth_provider.application;

import com.liminallabs.gateway.token.domain.Token;

import reactor.core.publisher.Mono;

/**
 * Porta para o provider de identidade (OAuth 2.0 Authorization Code + PKCE).
 * Falhas chegam como {@link InvalidGrantException} (grant rejeitado) ou
 * {@link AuthProviderUnavailableException} (qualquer outra coisa).
 */
public interface AuthProvider {

    String authorizationUrl(String redirectUri, String state, String codeChallenge);

    Mono<Token> exchangeCode(String code, String redirectUri, String codeVerifier);

    Mono<Token> refresh(String refreshToken);
}
