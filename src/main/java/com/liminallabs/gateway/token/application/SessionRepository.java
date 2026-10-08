package com.liminallabs.gateway.token.application;

import java.util.UUID;

import com.liminallabs.gateway.token.domain.Token;

import reactor.core.publisher.Mono;

/** Porta de persistência das sessões. */
public interface SessionRepository {

    Mono<Token> findById(UUID sessionId);

    Mono<Void> save(UUID sessionId, Token token);

    Mono<Boolean> existsById(UUID sessionId);

    Mono<Void> deleteById(UUID sessionId);
}
