package com.liminallabs.gateway.token.infra.mongo;

import static com.liminallabs.gateway.support.Tokens.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.liminallabs.gateway.token.domain.Token;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class MongoSessionRepositoryTest {

    @Mock
    private TokenRepository repository;

    private MongoSessionRepository sessions;

    @BeforeEach
    void setUp() {
        sessions = new MongoSessionRepository(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void saveStoresTokenAndExpirationFromIssuedAt() {
        UUID id = UUID.randomUUID();
        Token token = new Token("a", "r", 300, 1800, NOW.minusSeconds(100));
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        sessions.save(id, token).block();

        ArgumentCaptor<TokenDocument> saved = ArgumentCaptor.forClass(TokenDocument.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.minusSeconds(100).plusSeconds(1800));
        assertThat(saved.getValue().token().toToken()).isEqualTo(token);
    }

    @Test
    void tokenWithoutLifetimeIsStoredWithoutExpiration() {
        when(repository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        sessions.save(UUID.randomUUID(), new Token("a", "r", 0, 0, NOW)).block();

        ArgumentCaptor<TokenDocument> saved = ArgumentCaptor.forClass(TokenDocument.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().expiresAt()).isNull();
    }

    @Test
    void findMapsDocumentBackToToken() {
        UUID id = UUID.randomUUID();
        Token token = new Token("a", "r", 300, 1800, NOW);
        when(repository.findById(id)).thenReturn(Mono.just(TokenDocument.of(id, token, NOW)));

        assertThat(sessions.findById(id).block()).isEqualTo(token);
    }
}
