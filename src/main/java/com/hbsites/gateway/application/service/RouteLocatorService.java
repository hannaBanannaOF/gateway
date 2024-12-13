package com.hbsites.gateway.application.service;

import com.hbsites.gateway.infraestructure.mongo.GatewayPathDocument;
import com.hbsites.gateway.infraestructure.mongo.UpdatePathsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteLocatorService {
    @Autowired
    private UpdatePathsRepository repository;

    public List<GatewayPathDocument> findAll() {
        return repository.findAll();
    }
}
