package com.hbsites.gateway.infraestructure.filter;

import com.hbsites.gateway.domain.model.Token;
import com.hbsites.gateway.infraestructure.config.GatewayCustomProperties;
import com.hbsites.gateway.infraestructure.store.TokenStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

@Component
public class CustomBearerAuthFilter implements GlobalFilter, Ordered {

    @Autowired
    private GatewayCustomProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        req = tokenHandler(req);
        req = req.mutate().build();
        return chain.filter(exchange.mutate().request(req).build()).then(Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();

            // Intercepta o status de retorno depois do roteamento
            if (response.getStatusCode() == HttpStatus.UNAUTHORIZED && !response.isCommitted()) {
                byte[] bytes = getBytes(exchange.getRequest().getHeaders().getFirst("Original-Url"));
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                response.getHeaders().setContentLength(bytes.length);

                DataBuffer buffer = response.bufferFactory().wrap(bytes);
                return response.writeWith(Mono.just(buffer));
            }

            return Mono.empty();
        }));
    }

    private byte[] getBytes(String originalUrl) {
        String redirect = properties.getKeycloak().getBaseUrl() + "/realms/"+properties.getKeycloak().getRealm()+"/protocol/openid-connect/auth?client_id="+properties.getKeycloak().getClientId()+"&response_type=code&redirect_uri="+properties.getOauthCallbackUrl()+"&scope=openid";
        if (originalUrl != null) {
            redirect = redirect + "&state=" + originalUrl;
        }
        String json = "{\"redirectUrl\": \"" + redirect + "\"}";

        return json.getBytes(StandardCharsets.UTF_8);
    }

    private ServerHttpRequest tokenHandler(ServerHttpRequest req) {
        if (req.getCookies().containsKey(properties.getSessionCookieName())) {
            Token tokens;
            TokenStore store = TokenStore.getInstance();
            UUID tokenId = UUID.fromString(Objects.requireNonNull(req.getCookies().getFirst(properties.getSessionCookieName())).getValue());
            tokens = store.storedTokens.get(tokenId);
            if (tokens != null) {
                if (!tokens.isAccessTokenValid() && tokens.isRefreshTokenValid()) {
                    MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
                    data.add("grant_type", "refresh_token");
                    data.add("client_id", properties.getKeycloak().getClientId());
                    data.add("client_secret", properties.getKeycloak().getClientSecret());
                    data.add("refresh_token", tokens.getRefreshToken());
                    RestClient cli = RestClient.create(properties.getKeycloak().getBaseUrlInternal());
                    Token res = null;
                    try {
                        res = cli.post().uri("/realms/" + properties.getKeycloak().getRealm() + "/protocol/openid-connect/token")
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
                req = req.mutate()
                        .headers(headers -> {
                            headers.add("Authorization", "Bearer " + finalTokens.getAccessToken());
                            headers.remove("Cookie");
                        }).build();
            }
        }
        return req;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
