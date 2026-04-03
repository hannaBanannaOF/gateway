package com.liminallabs.gateway.token.infra.mongo;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TokenRepository extends MongoRepository<TokenDocument, UUID> {
}