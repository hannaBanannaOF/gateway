package com.liminallabs.gateway.token.infra.mongo;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.liminallabs.gateway.token.domain.Token;

@Document(collection = "tokens")
public record TokenDocument(@Id UUID id, Token token, Instant createdAt) {
}