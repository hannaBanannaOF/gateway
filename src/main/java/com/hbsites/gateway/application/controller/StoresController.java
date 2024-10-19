package com.hbsites.gateway.application.controller;

import com.hbsites.gateway.infraestructure.store.RoutesStore;
import com.hbsites.gateway.infraestructure.store.TokenStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores")
public class StoresController {

    @Value("${spring.profiles.active}")
    private String activeProfile;

    @PostMapping("/clear")
    public void clearStores() {
        if (!"docker-dev".equals(activeProfile) && !"dev".equals(activeProfile)) {
            throw new RuntimeException();
        }
        TokenStore.getInstance().clearAll();
        RoutesStore.getInstance().clearAll();
    }
}
