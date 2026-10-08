package com.liminallabs.gateway.token.infra.mongo;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.liminallabs.gateway.token.domain.Token;

/**
 * Sessão persistida. O Mongo remove o documento quando {@code expiresAt} passa (índice TTL).
 * {@code expiresAt} nulo = sessão sem expiração (o TTL ignora documentos sem o campo).
 */
@Document(collection = "tokens")
public record TokenDocument(
    @Id UUID id,
    StoredToken token,
    Instant createdAt,
    @Indexed(name = "tokens_expires_at_ttl", expireAfter = "0s") Instant expiresAt
) {

    static TokenDocument of(UUID id, Token token, Instant now) {
        return new TokenDocument(id, StoredToken.from(token), now, token.sessionExpiresAt());
    }

    /**
     * Mesmos nomes de campo que o {@code Token} antigo gravava, para que sessões já existentes
     * continuem legíveis ({@code creationDateTime} era LocalDateTime; no BSON ambos viram Date).
     */
    record StoredToken(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        Instant creationDateTime
    ) {

        static StoredToken from(Token token) {
            return new StoredToken(token.accessToken(), token.refreshToken(),
                token.expiresIn(), token.refreshExpiresIn(), token.issuedAt());
        }

        Token toToken() {
            return new Token(accessToken, refreshToken, expiresIn, refreshExpiresIn, creationDateTime);
        }
    }
}
