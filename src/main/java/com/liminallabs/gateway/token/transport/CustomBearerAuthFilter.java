package com.liminallabs.gateway.token.transport;

import java.util.Map;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liminallabs.gateway.auth_provider.application.AuthProviderUnavailableException;
import com.liminallabs.gateway.config.GatewayProperties;
import com.liminallabs.gateway.token.application.SessionService;
import com.liminallabs.gateway.token.domain.Token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/** Troca o cookie de sessão por {@code Authorization: Bearer} e transforma 401 do upstream em pedido de login. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomBearerAuthFilter implements GlobalFilter, Ordered {

    private final GatewayProperties properties;
    private final SessionCookies cookies;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.justOrEmpty(cookies.sessionId(exchange.getRequest()))
            .flatMap(sessionService::resolve)
            .map(token -> withBearer(exchange, token))
            .defaultIfEmpty(exchange)
            .flatMap(authorized -> chain.filter(authorized)
                .then(Mono.defer(() -> rewriteUnauthorized(exchange))))
            .onErrorResume(AuthProviderUnavailableException.class, e -> providerUnavailable(exchange, e));
    }

    private static ServerWebExchange withBearer(ServerWebExchange exchange, Token token) {
        return exchange.mutate()
            .request(req -> req.headers(headers -> {
                headers.setBearerAuth(token.accessToken());
                headers.remove(HttpHeaders.COOKIE);
            }))
            .build();
    }

    private Mono<Void> rewriteUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.getStatusCode() != HttpStatus.UNAUTHORIZED || response.isCommitted()) {
            return Mono.empty();
        }
        String loginUrl = loginUrl(exchange.getRequest().getHeaders().getFirst("Original-Url"));
        return writeJson(response, Map.of("redirectUrl", loginUrl));
    }

    /** A sessão continua viva: quando o provider voltar, o próximo request renova normalmente. */
    private Mono<Void> providerUnavailable(ServerWebExchange exchange, AuthProviderUnavailableException e) {
        log.warn("Provider de autenticação indisponível: {}", e.getMessage());
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        return writeJson(response, Map.of("error", "auth_provider_unavailable"));
    }

    private String loginUrl(String originalUrl) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(properties.resolvedLoginUrl());
        if (ReturnPath.isSafe(originalUrl)) {
            url.queryParam("returnTo", "{returnTo}");
            return url.encode().buildAndExpand(originalUrl).toUriString();
        }
        return url.toUriString();
    }

    private Mono<Void> writeJson(ServerHttpResponse response, Map<String, String> body) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
