package com.liminallabs.gateway.token.infra.mongo;

import java.util.UUID;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

interface TokenRepository extends ReactiveMongoRepository<TokenDocument, UUID> {
}
