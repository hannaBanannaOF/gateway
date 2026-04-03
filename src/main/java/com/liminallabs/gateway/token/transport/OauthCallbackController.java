package com.liminallabs.gateway.token.transport;

import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.properties.domain.GatewayCustomProperties;
import com.liminallabs.gateway.token.application.TokenStore;
import com.liminallabs.gateway.token.domain.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.UUID;

@RestController
public class OauthCallbackController {

    @Autowired
    private GatewayCustomProperties properties;
    
    @Autowired
    private TokenStore tokenStore;
    
    @Autowired
    private AuthProvider authProvider;

    @Value("${spring.profiles.active:PRD}")
    private String activeProfile;

    @GetMapping("/oauth/callback")
    public Mono<Void> auth(ServerWebExchange exchange) {
        String code = exchange.getRequest().getQueryParams().getFirst("code");
        
        if (code == null) {
            return Mono.error(new IllegalArgumentException("Authorization code is missing"));
        }

        // Troca o código pelo token usando o provider configurado
        return Mono.fromCallable(() -> {
            return authProvider.exchangeCodeForToken(code, properties.getOauthCallbackUrl());
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(token -> {
            // Monta a URL de redirecionamento
            String redirectUri = buildRedirectUri(
                exchange.getRequest().getQueryParams().getFirst("state")
            );

            // Cria a sessão e o cookie
            UUID sessionTicket = tokenStore.createTokenEntry(token);
            
            exchange.getResponse().setStatusCode(HttpStatus.PERMANENT_REDIRECT);
            exchange.getResponse().getHeaders().setLocation(URI.create(redirectUri));
            exchange.getResponse().addCookie(
                buildSessionCookie(sessionTicket.toString())
            );
            
            return exchange.getResponse().setComplete();
        });
    }

    private String buildRedirectUri(String state) {
        String redirectUri = properties.getFrontendUrl();
        if (state != null && !state.isBlank()) {
            redirectUri = redirectUri + state;
        }
        return redirectUri;
    }

    private ResponseCookie buildSessionCookie(String sessionId) {
        return ResponseCookie.from(properties.getSessionCookieName(), sessionId)
            .path("/")
            .domain(properties.getCookieDomain())
            .sameSite(properties.getCookieSameSite())
            .secure(properties.isCookieSecure())
            .httpOnly(true) // Adicionar segurança
            .maxAge(properties.getCookieMaxAge())
            .build();
    }

    @PostMapping("/oauth/bypass")
    public String bypassAuth(@RequestBody Token token) {
        validateDevProfile();
        return tokenStore.createTokenEntry(token).toString();
    }

    @PutMapping("/oauth/bypass/{id}")
    public void bypassAuthUpdate(@RequestBody Token token, @PathVariable UUID id) {
        validateDevProfile();
        tokenStore.updateToken(id, token);
    }

    private void validateDevProfile() {
        if (!"dev".equals(activeProfile)) {
            throw new IllegalStateException("Bypass endpoints are only available in dev profile");
        }
    }
}
