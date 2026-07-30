package com.liminallabs.gateway.auth_provider.infra;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.token.domain.Token;

@Component
@ConditionalOnProperty(name = "liminallabs.gateway.auth.provider-name", havingValue = "keycloak")
public class KeycloakAuthProvider implements AuthProvider {

    @Autowired
    private KeycloakProperties props;

    @Override
    public String getAuthorizationUrl(String redirectUri, String state) {
        StringBuilder url = new StringBuilder()
            .append(props.getBaseUrl())
            .append("/realms/").append(props.getRealm())
            .append("/protocol/openid-connect/auth")
            .append("?client_id=").append(props.getClientId())
            .append("&response_type=code")
            .append("&redirect_uri=").append(redirectUri)
            .append("&scope=openid");

        if (state != null) {
            url.append("&state=").append(state);
        }

        return url.toString();
    }

    @Override
    public Token exchangeCodeForToken(String code, String redirectUri) {
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("redirect_uri", redirectUri);
        data.add("grant_type", "authorization_code");
        data.add("client_id", props.getClientId());
        data.add("client_secret", props.getClientSecret());
        data.add("code", code);
        data.add("scope", "openid");

        try {
            return restClient().post()
                .uri("/realms/" + props.getRealm() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(data)
                .retrieve()
                .body(Token.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to exchange code for token", e);
        }
    }

    @Override
    public Token refreshToken(String refreshToken) {
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("grant_type", "refresh_token");
        data.add("client_id", props.getClientId());
        data.add("client_secret", props.getClientSecret());
        data.add("refresh_token", refreshToken);

        try {
            return restClient().post()
                .uri("/realms/" + props.getRealm() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(data)
                .retrieve()
                .body(Token.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean validateToken(String token) {
        return true;
    }

    @Override
    public String getProviderName() {
        return "keycloak";
    }

    private RestClient restClient() {
        return RestClient.create(props.getBaseUrlInternal());
    }
}
