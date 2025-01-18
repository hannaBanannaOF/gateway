package com.hbsites.gateway.application.controller;

import com.hbsites.gateway.domain.model.Token;
import com.hbsites.gateway.infraestructure.config.GatewayCustomProperties;
import com.hbsites.gateway.infraestructure.store.TokenStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@RestController
public class OauthCallbackController {

    @Autowired
    private GatewayCustomProperties properties;

    @Value("${spring.profiles.active}")
    private String activeProfile;

    @GetMapping("/oauth/callback")
    public Mono<Void> auth(ServerWebExchange exchange) {
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("redirect_uri", properties.getOauthCallbackUrl());
        data.add("grant_type", "authorization_code");
        data.add("client_id", properties.getKeycloak().getClientId());
        data.add("client_secret", properties.getKeycloak().getClientSecret());
        data.add("code", exchange.getRequest().getQueryParams().getFirst("code"));
        data.add("scope", "openid");

        RestClient cli = RestClient.create(properties.getKeycloak().getBaseUrl());
        Token res = cli.post().uri("/realms/"+properties.getKeycloak().getRealm()+"/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(data).retrieve().body(Token.class);

        TokenStore store = TokenStore.getInstance();
        UUID sessionTicket = store.createTokenEntry(res);
        exchange.getResponse().setStatusCode(HttpStatus.PERMANENT_REDIRECT);
        exchange.getResponse().getHeaders().setLocation(URI.create(properties.getFrontendUrl()));
        exchange.getResponse().addCookie(ResponseCookie.from(properties.getSessionCookieName(), sessionTicket.toString())
            .path("/")
            .domain("localhost")
            .sameSite("Strict")
            .build()
        );
        return exchange.getResponse().setComplete();
    }

    @PostMapping("/oauth/bypass")
    public String bypassAuth(@RequestBody Token t) {
        if (!"docker-dev".equals(activeProfile) && !"dev".equals(activeProfile)) {
            throw new RuntimeException();
        }
        TokenStore s = TokenStore.getInstance();
        return s.createTokenEntry(t).toString();
    }

    @PutMapping("/oauth/bypass/{id}")
    public void bypassAuthUpdate(@RequestBody Token t, @PathVariable UUID id) {
        if (!"docker-dev".equals(activeProfile) && !"dev".equals(activeProfile)) {
            throw new RuntimeException();
        }
        TokenStore s = TokenStore.getInstance();
        s.updateToken(id, t);
    }

}
