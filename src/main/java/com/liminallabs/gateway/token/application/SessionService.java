package com.liminallabs.gateway.token.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.auth_provider.application.InvalidGrantException;
import com.liminallabs.gateway.token.domain.Token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Ciclo de vida das sessões. O Mongo é a única fonte de verdade: sem cache local,
 * então todas as réplicas enxergam o mesmo token logo depois de um refresh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository repository;
    private final AuthProvider authProvider;
    private final Clock clock;

    /** Refresh em andamento por sessão: requisições paralelas da mesma sessão compartilham o resultado. */
    private final ConcurrentMap<UUID, Mono<Token>> refreshesInFlight = new ConcurrentHashMap<>();

    public Mono<UUID> create(Token token) {
        UUID id = UUID.randomUUID();
        return repository.save(id, token).thenReturn(id);
    }

    /** Substitui o token de uma sessão existente; sessão desconhecida é ignorada. */
    public Mono<Void> update(UUID sessionId, Token token) {
        return repository.existsById(sessionId)
            .filter(Boolean::booleanValue)
            .flatMap(exists -> repository.save(sessionId, token));
    }

    public Mono<Void> remove(UUID sessionId) {
        return repository.deleteById(sessionId);
    }

    /**
     * Devolve um token com access token válido para a sessão, renovando se preciso.
     * Vazio quando a sessão não existe, expirou ou o refresh foi rejeitado.
     * Propaga {@code AuthProviderUnavailableException} se o provider estiver fora.
     */
    public Mono<Token> resolve(UUID sessionId) {
        return findActive(sessionId)
            .flatMap(token -> token.isAccessTokenValid(clock.instant()) || !token.isRefreshTokenValid(clock.instant())
                ? Mono.just(token)
                : refreshOnce(sessionId, token));
    }

    /** Sessão expirada conta como inexistente: o TTL do Mongo roda a cada ~60s e o documento pode demorar a sumir. */
    private Mono<Token> findActive(UUID sessionId) {
        return repository.findById(sessionId).filter(token -> !token.isExpired(clock.instant()));
    }

    private Mono<Token> refreshOnce(UUID sessionId, Token stale) {
        return refreshesInFlight.computeIfAbsent(sessionId, id -> refresh(id, stale)
            .doFinally(signal -> refreshesInFlight.remove(id))
            .cache());
    }

    private Mono<Token> refresh(UUID sessionId, Token stale) {
        return authProvider.refresh(stale.refreshToken())
            .map(fresh -> fresh.withRefreshTokenFallback(stale.refreshToken()))
            .flatMap(fresh -> repository.save(sessionId, fresh).thenReturn(fresh))
            .onErrorResume(InvalidGrantException.class, e -> recoverFromRejectedRefresh(sessionId, stale, e));
    }

    /**
     * Com rotação de refresh token, outra réplica pode ter renovado primeiro e invalidado o token que usamos.
     * Nesse caso a sessão persistida já tem um token novo e válido: usa ele em vez de deslogar o usuário.
     */
    private Mono<Token> recoverFromRejectedRefresh(UUID sessionId, Token stale, InvalidGrantException cause) {
        return findActive(sessionId)
            .filter(current -> !Objects.equals(current.refreshToken(), stale.refreshToken()))
            .filter(current -> current.isAccessTokenValid(clock.instant()))
            .switchIfEmpty(Mono.defer(() -> {
                log.info("Refresh rejeitado, encerrando a sessão {}: {}", sessionId, cause.getMessage());
                return repository.deleteById(sessionId).then(Mono.empty());
            }));
    }
}
