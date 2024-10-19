package com.hbsites.gateway.infraestructure.store;

import com.hbsites.gateway.domain.model.Token;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TokenStore {

    private static TokenStore instance;

    private TokenStore() {

    }

    public static TokenStore getInstance() {
        if (instance == null) {
            instance = new TokenStore();
        }
        return instance;
    }

    public Map<UUID, Token> storedTokens = new HashMap<>();

    public UUID createTokenEntry(Token tokens) {
        UUID id = UUID.randomUUID();
        storedTokens.put(id, tokens);
        return id;
    }

    public void updateToken(UUID tokenId, Token tokens) {
        storedTokens.put(tokenId, tokens);
    }

    public void removeTokens(UUID tokenId) {
        storedTokens.remove(tokenId);
    }

    public void clearAll() {
        this.storedTokens.clear();
    }
}
