package com.liminallabs.gateway.token.infra.mongo;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.liminallabs.gateway.token.application.SessionRepository;
import com.liminallabs.gateway.token.domain.Token;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
class MongoSessionRepository implements SessionRepository {

    private final TokenRepository repository;
    private final Clock clock;

    @Override
    public Mono<Token> findById(UUID sessionId) {
        return repository.findById(sessionId).map(doc -> doc.token().toToken());
    }

    @Override
    public Mono<Void> save(UUID sessionId, Token token) {
        return repository.save(TokenDocument.of(sessionId, token, clock.instant())).then();
    }

    @Override
    public Mono<Boolean> existsById(UUID sessionId) {
        return repository.existsById(sessionId);
    }

    @Override
    public Mono<Void> deleteById(UUID sessionId) {
        return repository.deleteById(sessionId);
    }
}
