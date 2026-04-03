package com.liminallabs.gateway.token.application;

import com.liminallabs.gateway.token.domain.Token;
import com.liminallabs.gateway.token.infra.mongo.TokenDocument;
import com.liminallabs.gateway.token.infra.mongo.TokenRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class TokenStore {

    @Autowired
    private TokenRepository repository;

    // cache opcional (thread-safe)
    private final Map<UUID, Token> cache = new ConcurrentHashMap<>();

    public TokenStore(TokenRepository repository) {
        this.repository = repository;
    }

    public UUID createTokenEntry(Token token) {
        UUID id = UUID.randomUUID();

        TokenDocument doc = new TokenDocument(id, token, Instant.now());

        repository.save(doc);
        cache.put(id, token);

        return id;
    }

    public void updateToken(@NonNull UUID tokenId, Token token) {
        repository.findById(tokenId).ifPresent(doc -> {
            TokenDocument newDoc = new TokenDocument(doc.id(), token, Instant.now());
            repository.save(newDoc);
            cache.put(tokenId, token);
        });
    }

    public Token getToken(@NonNull UUID tokenId) {
        // ⚡ tenta cache primeiro
        Token cached = cache.get(tokenId);
        if (cached != null) return cached;

        // 💾 fallback pro Mongo
        return repository.findById(tokenId)
                .map(doc -> {
                    cache.put(tokenId, doc.token());
                    return doc.token();
                })
                .orElse(null);
    }

    public void removeTokens(@NonNull UUID tokenId) {
        repository.deleteById(tokenId);
        cache.remove(tokenId);
    }

    public void clearAll() {
        repository.deleteAll();
        cache.clear();
    }
}
