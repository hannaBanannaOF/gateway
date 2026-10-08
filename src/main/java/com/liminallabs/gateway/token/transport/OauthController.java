package com.liminallabs.gateway.token.transport;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.auth_provider.application.AuthProviderUnavailableException;
import com.liminallabs.gateway.auth_provider.application.InvalidGrantException;
import com.liminallabs.gateway.config.GatewayProperties;
import com.liminallabs.gateway.token.application.SessionService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/** Fluxo OAuth 2.0 Authorization Code com state + PKCE. */
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OauthController {

    private final GatewayProperties properties;
    private final SessionCookies cookies;
    private final SessionService sessionService;
    private final AuthProvider authProvider;

    /** Ponto de entrada do login: o state e o PKCE nascem aqui, só quando o navegador navega de fato. */
    @GetMapping("/login")
    public Mono<Void> login(@RequestParam(required = false) String returnTo, ServerWebExchange exchange) {
        LoginRequest login = LoginRequest.start(returnTo);
        String authorizationUrl = authProvider.authorizationUrl(
            properties.oauthCallbackUrl(), login.state(), login.pkce().challenge());

        ServerHttpResponse response = exchange.getResponse();
        response.addCookie(cookies.login(login));
        return redirect(response, authorizationUrl);
    }

    @GetMapping("/callback")
    public Mono<Void> callback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state,
        ServerWebExchange exchange
    ) {
        if (code == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization code is missing"));
        }
        LoginRequest login = cookies.login(exchange.getRequest())
            .filter(pending -> pending.matchesState(state))
            .orElse(null);
        if (login == null) {
            // Sem cookie ou state diferente: possível login CSRF, ou o login começou em outra aba
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OAuth state"));
        }

        return authProvider.exchangeCode(code, properties.oauthCallbackUrl(), login.pkce().verifier())
            .flatMap(sessionService::create)
            .flatMap(sessionId -> {
                ServerHttpResponse response = exchange.getResponse();
                response.addCookie(cookies.session(sessionId));
                response.addCookie(cookies.clearLogin());
                return redirect(response, frontendUrl(login.returnTo()));
            })
            .onErrorMap(InvalidGrantException.class,
                e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authorization code rejected", e))
            .onErrorMap(AuthProviderUnavailableException.class,
                e -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Auth provider unavailable", e));
    }

    private String frontendUrl(String returnTo) {
        return ReturnPath.isSafe(returnTo) ? properties.frontendUrl() + returnTo : properties.frontendUrl();
    }

    private static Mono<Void> redirect(ServerHttpResponse response, String location) {
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(location));
        return response.setComplete();
    }
}
