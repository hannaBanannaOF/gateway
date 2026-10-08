package com.liminallabs.gateway.auth_provider.infra.kc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liminallabs.gateway.auth_provider.application.AuthProvider;
import com.liminallabs.gateway.auth_provider.application.AuthProviderUnavailableException;
import com.liminallabs.gateway.auth_provider.application.InvalidGrantException;
import com.liminallabs.gateway.auth_provider.application.Pkce;
import com.liminallabs.gateway.token.domain.Token;

import reactor.core.publisher.Mono;

public class KeycloakAuthProvider implements AuthProvider {

    private final KeycloakProperties props;
    private final WebClient webClient;
    private final Clock clock;

    KeycloakAuthProvider(KeycloakProperties props, WebClient.Builder webClientBuilder, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.webClient = webClientBuilder.baseUrl(props.baseUrlInternal()).build();
    }

    @Override
    public String authorizationUrl(String redirectUri, String state, String codeChallenge) {
        return props.baseUrl()
            + realmPath() + "/auth"
            + "?client_id=" + encode(props.clientId())
            + "&response_type=code"
            + "&redirect_uri=" + encode(redirectUri)
            + "&scope=openid"
            + "&state=" + encode(state)
            + "&code_challenge=" + encode(codeChallenge)
            + "&code_challenge_method=" + Pkce.METHOD;
    }

    @Override
    public Mono<Token> exchangeCode(String code, String redirectUri, String codeVerifier) {
        MultiValueMap<String, String> form = clientForm("authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        return requestToken(form, "troca do code");
    }

    @Override
    public Mono<Token> refresh(String refreshToken) {
        MultiValueMap<String, String> form = clientForm("refresh_token");
        form.add("refresh_token", refreshToken);
        return requestToken(form, "refresh");
    }

    private Mono<Token> requestToken(MultiValueMap<String, String> form, String operation) {
        return Mono.defer(() -> {
            // Conta a validade a partir do envio: conservador se a resposta demorar
            Instant requestedAt = clock.instant();
            return webClient.post()
                .uri(realmPath() + "/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(response -> response.statusCode().is2xxSuccessful()
                    ? response.bodyToMono(KeycloakTokenResponse.class)
                    : response.bodyToMono(OAuthError.class)
                        .onErrorResume(e -> Mono.empty())
                        .defaultIfEmpty(OAuthError.UNKNOWN)
                        .flatMap(error -> Mono.error(toException(response.statusCode(), error, operation))))
                .map(response -> response.toToken(requestedAt));
        })
            .timeout(props.timeout())
            .onErrorMap(e -> !(e instanceof InvalidGrantException || e instanceof AuthProviderUnavailableException),
                e -> new AuthProviderUnavailableException("Keycloak: " + operation + " falhou", e));
    }

    private static RuntimeException toException(HttpStatusCode status, OAuthError error, String operation) {
        // Só invalid_grant diz respeito ao token do usuário; invalid_client etc. é problema de configuração
        if (status.value() == HttpStatus.BAD_REQUEST.value() && "invalid_grant".equals(error.error())) {
            return new InvalidGrantException("Keycloak rejeitou o grant na " + operation + ": " + error.description());
        }
        return new AuthProviderUnavailableException(
            "Keycloak respondeu " + status.value() + " (" + error.error() + ") na " + operation, null);
    }

    private MultiValueMap<String, String> clientForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        return form;
    }

    private String realmPath() {
        return "/realms/" + props.realm() + "/protocol/openid-connect";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record OAuthError(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String description
    ) {
        static final OAuthError UNKNOWN = new OAuthError("unknown", null);
    }
}
