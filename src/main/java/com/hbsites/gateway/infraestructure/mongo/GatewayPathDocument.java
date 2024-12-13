package com.hbsites.gateway.infraestructure.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public record GatewayPathDocument(@Id String id, String instance, String regex, String uri) {
}
