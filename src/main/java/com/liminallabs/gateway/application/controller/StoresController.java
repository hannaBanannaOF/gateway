package com.liminallabs.gateway.application.controller;

import com.liminallabs.gateway.infraestructure.store.TokenStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stores")
public class StoresController {

    @Value("${spring.profiles.active:PRD}")
    private String activeProfile;

    @PostMapping("/clear")
    public void clearStores() {
        if (!"dev".equals(activeProfile)) {
            throw new RuntimeException();
        }
        TokenStore.getInstance().clearAll();
    }
}
