package com.liminallabs.gateway.token.infra;

import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.properties.domain.GatewayCustomProperties;
import com.liminallabs.gateway.token.application.TokenStore;
import com.liminallabs.gateway.token.domain.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class CustomBearerAuthFilter implements GlobalFilter, Ordered {

    @Autowired
    private GatewayCustomProperties properties;
    
    @Autowired
    private TokenStore tokenStore;
    
    @Autowired
    private AuthProvider authProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        req = tokenHandler(req);
        req = req.mutate().build();
        
        return chain.filter(exchange.mutate().request(req).build())
            .then(Mono.defer(() -> {
                ServerHttpResponse response = exchange.getResponse();

                if (response.getStatusCode() == HttpStatus.UNAUTHORIZED && !response.isCommitted()) {
                    byte[] bytes = buildRedirectResponse(
                        exchange.getRequest().getHeaders().getFirst("Original-Url")
                    );
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    response.getHeaders().setContentLength(bytes.length);

                    DataBuffer buffer = response.bufferFactory().wrap(bytes);
                    return response.writeWith(Mono.just(buffer));
                }

                return Mono.empty();
            }));
    }

    private byte[] buildRedirectResponse(String originalUrl) {
        String redirect = authProvider.getAuthorizationUrl(
            properties.getOauthCallbackUrl(), 
            originalUrl
        );
        
        String json = "{\"redirectUrl\": \"" + redirect + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private ServerHttpRequest tokenHandler(ServerHttpRequest req) {
        String sessionCookieName = properties.getSessionCookieName();
        if (sessionCookieName == null) {
            return req;
        }
        
        if (!req.getCookies().containsKey(sessionCookieName)) {
            return req;
        }

        HttpCookie cookie = req.getCookies().getFirst(sessionCookieName);
        if (cookie == null) {
            return req;
        }

        UUID tokenId = UUID.fromString(cookie.getValue());
        Token tokens = tokenStore.getToken(tokenId);
        
        if (tokens == null) {
            return req;
        }

        // Refresh token se necessário
        if (!tokens.isAccessTokenValid() && tokens.isRefreshTokenValid()) {
            Token refreshedToken = authProvider.refreshToken(tokens.getRefreshToken());
            
            if (refreshedToken != null) {
                tokenStore.updateToken(tokenId, refreshedToken);
                tokens = refreshedToken;
            } else {
                tokens.setAccessToken("");
                tokenStore.removeTokens(tokenId);
            }
        }

        Token finalTokens = tokens;
        return req.mutate()
            .headers(headers -> {
                headers.add("Authorization", "Bearer " + finalTokens.getAccessToken());
                headers.remove("Cookie");
            })
            .build();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
