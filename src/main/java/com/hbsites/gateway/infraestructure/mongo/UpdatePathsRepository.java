package com.hbsites.gateway.infraestructure.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UpdatePathsRepository extends MongoRepository<GatewayPathDocument, String> {
    GatewayPathDocument findByInstance(String instance);
}
