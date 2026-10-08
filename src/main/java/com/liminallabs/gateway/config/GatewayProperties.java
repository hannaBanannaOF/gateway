package com.liminallabs.gateway.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Configuração do gateway.
 *
 * @param sessionCookieName nome do cookie que carrega o ID da sessão
 * @param oauthCallbackUrl URL pública do callback OAuth registrada no provider
 * @param loginUrl URL pública do endpoint de login do gateway; se vazia, é derivada do callback ({@code .../oauth/login})
 * @param frontendUrl URL base para onde o navegador volta depois do login
 * @param cookieDomain atributo Domain do cookie de sessão; vazio omite o atributo
 * @param cookieSameSite atributo SameSite do cookie de sessão
 * @param cookieSecure se o cookie de sessão leva a flag Secure
 * @param cookieMaxAge Max-Age do cookie de sessão, em segundos
 * @param auth configuração do provider de autenticação
 */
@Validated
@ConfigurationProperties(prefix = "liminallabs.gateway")
public record GatewayProperties(
    @NotBlank String sessionCookieName,
    @NotBlank String oauthCallbackUrl,
    String loginUrl,
    @NotBlank String frontendUrl,
    String cookieDomain,
    @DefaultValue("Strict") String cookieSameSite,
    boolean cookieSecure,
    @DefaultValue("3600") long cookieMaxAge,
    @Valid @NotNull Auth auth
) {

    /**
     * @param providerName qual provider de autenticação ativar (hoje só {@code keycloak})
     */
    public record Auth(@NotBlank String providerName) {
    }

    public String resolvedLoginUrl() {
        if (loginUrl != null && !loginUrl.isBlank()) {
            return loginUrl;
        }
        // Relativo ao callback preserva prefixos de path: http://host/gw/oauth/callback -> http://host/gw/oauth/login
        return URI.create(oauthCallbackUrl).resolve("login").toString();
    }

    public boolean hasCookieDomain() {
        return cookieDomain != null && !cookieDomain.isBlank();
    }
}
