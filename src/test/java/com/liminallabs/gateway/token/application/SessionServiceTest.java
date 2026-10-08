package com.liminallabs.gateway.token.application;

import static com.liminallabs.gateway.support.Tokens.NOW;
import static com.liminallabs.gateway.support.Tokens.accessExpired;
import static com.liminallabs.gateway.support.Tokens.fresh;
import static com.liminallabs.gateway.support.Tokens.token;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.auth_provider.application.AuthProviderUnavailableException;
import com.liminallabs.gateway.auth_provider.application.InvalidGrantException;
import com.liminallabs.gateway.token.domain.Token;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class SessionServiceTest {

    private final InMemorySessions sessions = new InMemorySessions();
    private final FakeProvider provider = new FakeProvider();
    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(sessions, provider, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createPersistsUnderNewId() {
        UUID id = service.create(fresh("a")).block();

        assertThat(sessions.data).containsEntry(id, fresh("a"));
    }

    @Test
    void updateReplacesExistingSessionOnly() {
        UUID id = UUID.randomUUID();
        sessions.data.put(id, fresh("old"));
        UUID unknown = UUID.randomUUID();

        service.update(id, fresh("new")).block();
        service.update(unknown, fresh("x")).block();

        assertThat(sessions.data).containsEntry(id, fresh("new")).doesNotContainKey(unknown);
    }

    @Test
    void validTokenIsReturnedWithoutRefresh() {
        UUID id = put(fresh("a"));

        assertThat(service.resolve(id).block()).isEqualTo(fresh("a"));
        assertThat(provider.calls).hasValue(0);
    }

    @Test
    void unknownOrExpiredSessionResolvesEmpty() {
        UUID expired = put(token("a", "r", 300, 1800, 3600));

        StepVerifier.create(service.resolve(UUID.randomUUID())).verifyComplete();
        StepVerifier.create(service.resolve(expired)).verifyComplete();
    }

    @Test
    void expiredAccessTokenIsRefreshedAndPersisted() {
        UUID id = put(accessExpired("a1", "r1"));
        provider.next = Mono.just(token("a2", "r2", 300, 1800, 0));

        assertThat(service.resolve(id).block().accessToken()).isEqualTo("a2");
        assertThat(sessions.data.get(id).refreshToken()).isEqualTo("r2");
        assertThat(provider.lastRefreshToken).isEqualTo("r1");
    }

    @Test
    void refreshWithoutNewRefreshTokenKeepsPreviousOne() {
        UUID id = put(accessExpired("a1", "r1"));
        provider.next = Mono.just(token("a2", null, 300, 1800, 0));

        service.resolve(id).block();

        assertThat(sessions.data.get(id).refreshToken()).isEqualTo("r1");
    }

    @Test
    void concurrentRequestsShareASingleRefresh() {
        UUID id = put(accessExpired("a1", "r1"));
        Sinks.One<Token> pending = Sinks.one();
        provider.next = pending.asMono();

        List<Mono<Token>> requests = List.of(service.resolve(id), service.resolve(id), service.resolve(id));
        Mono<List<Token>> all = Flux.merge(requests).collectList();

        StepVerifier.create(all)
            .then(() -> pending.tryEmitValue(token("a2", "r2", 300, 1800, 0)))
            .assertNext(tokens -> assertThat(tokens).extracting(Token::accessToken).containsOnly("a2").hasSize(3))
            .verifyComplete();
        assertThat(provider.calls).hasValue(1);
    }

    @Test
    void afterRefreshCompletesANewRefreshCanHappen() {
        UUID id = put(accessExpired("a1", "r1"));
        provider.next = Mono.just(accessExpired("a2", "r2"));
        service.resolve(id).block();

        provider.next = Mono.just(fresh("a3"));
        assertThat(service.resolve(id).block().accessToken()).isEqualTo("a3");
        assertThat(provider.calls).hasValue(2);
    }

    @Test
    void rejectedRefreshEndsSession() {
        UUID id = put(accessExpired("a1", "r1"));
        provider.next = Mono.error(new InvalidGrantException("revoked"));

        StepVerifier.create(service.resolve(id)).verifyComplete();
        assertThat(sessions.data).doesNotContainKey(id);
    }

    @Test
    void rejectedRefreshUsesTokenAlreadyRenewedByAnotherReplica() {
        UUID id = put(accessExpired("a1", "r1"));
        // Outra réplica renovou entre a nossa leitura e o nosso refresh: o r1 foi rotacionado
        provider.next = Mono.defer(() -> {
            sessions.data.put(id, token("a2", "r2", 300, 1800, 0));
            return Mono.error(new InvalidGrantException("rotated"));
        });

        assertThat(service.resolve(id).block().accessToken()).isEqualTo("a2");
        assertThat(sessions.data).containsKey(id);
    }

    @Test
    void transientProviderFailurePropagatesAndKeepsSession() {
        UUID id = put(accessExpired("a1", "r1"));
        provider.next = Mono.error(new AuthProviderUnavailableException("down", null));

        StepVerifier.create(service.resolve(id)).expectError(AuthProviderUnavailableException.class).verify();
        assertThat(sessions.data).containsKey(id);
    }

    @Test
    void removeDeletesSession() {
        UUID id = put(fresh("a"));

        service.remove(id).block();

        assertThat(sessions.data).doesNotContainKey(id);
    }

    private UUID put(Token token) {
        UUID id = UUID.randomUUID();
        sessions.data.put(id, token);
        return id;
    }

    private static class InMemorySessions implements SessionRepository {
        final Map<UUID, Token> data = new ConcurrentHashMap<>();

        @Override
        public Mono<Token> findById(UUID id) {
            return Mono.fromSupplier(() -> data.get(id));
        }

        @Override
        public Mono<Void> save(UUID id, Token token) {
            return Mono.fromRunnable(() -> data.put(id, token));
        }

        @Override
        public Mono<Boolean> existsById(UUID id) {
            return Mono.fromSupplier(() -> data.containsKey(id));
        }

        @Override
        public Mono<Void> deleteById(UUID id) {
            return Mono.fromRunnable(() -> data.remove(id));
        }
    }

    private static class FakeProvider implements AuthProvider {
        final AtomicInteger calls = new AtomicInteger();
        volatile Mono<Token> next = Mono.error(new AssertionError("refresh inesperado"));
        volatile String lastRefreshToken;

        @Override
        public Mono<Token> refresh(String refreshToken) {
            return Mono.defer(() -> {
                calls.incrementAndGet();
                lastRefreshToken = refreshToken;
                return next;
            });
        }

        @Override
        public String authorizationUrl(String redirectUri, String state, String codeChallenge) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Token> exchangeCode(String code, String redirectUri, String codeVerifier) {
            throw new UnsupportedOperationException();
        }
    }
}
