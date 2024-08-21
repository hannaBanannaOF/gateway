package com.hbsites.gateway.infraestructure.filter;

import com.hbsites.gateway.domain.model.Token;
import com.hbsites.gateway.infraestructure.config.GatewayCustomProperties;
import com.hbsites.gateway.infraestructure.store.TokenStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@Component
public class CustomBearerAuthFilter implements GlobalFilter {

    @Autowired
    private GatewayCustomProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        if (req.getCookies().containsKey(properties.getSessionCookieName())) {
            req = updateHeaders(req);
        }
        return chain.filter(exchange.mutate().request(req).build()).then(Mono.fromRunnable(() -> {
            var response = exchange.getResponse();
            if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                response.setStatusCode(HttpStatus.PERMANENT_REDIRECT);
                response.getHeaders().setCacheControl(CacheControl.noCache());
                response.getHeaders().setLocation(URI.create(properties.getKeycloak().getRedirectUrl()+"/realms/"+properties.getKeycloak().getRealm()+"/protocol/openid-connect/auth?client_id="+properties.getKeycloak().getClientId()+"&response_type=code&redirect_uri="+properties.getOauthCallbackUrl()+"&scope=openid"));            }
        }));
    }

    private ServerHttpRequest updateHeaders(ServerHttpRequest oldReq) {
        Token tokens;
        TokenStore store = TokenStore.getInstance();
        UUID tokenId = UUID.fromString(Objects.requireNonNull(oldReq.getCookies().getFirst(properties.getSessionCookieName())).getValue());
        tokens = store.storedTokens.get(tokenId);
        if (tokens != null) {

            if (!tokens.isAccessTokenValid() && tokens.isRefreshTokenValid()) {
                System.out.println("refresh");
                MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
                data.add("grant_type", "refresh_token");
                data.add("client_id", properties.getKeycloak().getClientId());
                data.add("client_secret", properties.getKeycloak().getClientSecret());
                data.add("refresh_token", tokens.getRefreshToken());
                RestClient cli = RestClient.create(properties.getKeycloak().getBaseUrl());
                Token res = null;
                try {
                    res = cli.post().uri("/realms/"+properties.getKeycloak().getRealm()+"/protocol/openid-connect/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(data).retrieve().body(Token.class);

                } catch (Exception e) {
                    // ignored
                }
                if (res != null) {
                    store.updateToken(tokenId, res);
                    tokens = res;
                } else {
                    tokens.setAccessToken("");
                    store.removeTokens(tokenId);
                }
            }

            Token finalTokens = tokens;
            return oldReq.mutate()
                    .headers(headers -> {
                        headers.add("Authorization", "Bearer "+ finalTokens.getAccessToken());
                        headers.remove("Cookie");
                    }).build();
        }
        return oldReq;
    }
}