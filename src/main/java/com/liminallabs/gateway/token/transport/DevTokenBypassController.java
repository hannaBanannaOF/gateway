package com.liminallabs.gateway.token.transport;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.liminallabs.gateway.token.application.TokenStore;
import com.liminallabs.gateway.token.domain.Token;

@RestController
@RequestMapping("/oauth/bypass")
@ConditionalOnProperty(name = "liminallabs.gateway.security.allow-token-bypass", havingValue = "true")
public class DevTokenBypassController {

    @Autowired
    private TokenStore tokenStore;

    @PostMapping
    public String bypassAuth(@RequestBody Token token) {
        return tokenStore.createTokenEntry(token).toString();
    }

    @PutMapping("/{id}")
    public void bypassAuthUpdate(@RequestBody Token token, @PathVariable UUID id) {
        tokenStore.updateToken(id, token);
    }
}
