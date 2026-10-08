package com.liminallabs.gateway.token.infra.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.StreamSupport;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class TokenDocumentIndexTest {

    @Test
    void expiresAtHasTtlIndexThatExpiresAtTheStoredInstant() {
        // Mesmos tipos simples que o Boot registra (java.time etc.)
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(context);

        List<IndexDefinition> indexes = StreamSupport
            .stream(resolver.resolveIndexFor(TokenDocument.class).spliterator(), false)
            .map(IndexDefinition.class::cast)
            .toList();

        assertThat(indexes).hasSize(1);
        Document options = indexes.get(0).getIndexOptions();
        assertThat(indexes.get(0).getIndexKeys()).containsKey("expiresAt");
        assertThat(options.get("name")).isEqualTo("tokens_expires_at_ttl");
        assertThat(options.get("expireAfterSeconds")).isEqualTo(0L);
    }
}
