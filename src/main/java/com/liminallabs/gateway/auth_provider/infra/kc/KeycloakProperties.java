package com.liminallabs.gateway.auth_provider.infra.kc;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configuração do Keycloak. Só é registrada quando {@code auth.provider-name=keycloak}.
 *
 * @param baseUrl URL pública do Keycloak, usada para redirecionar o navegador ao login
 * @param baseUrlInternal URL interna do Keycloak, usada nas chamadas servidor-a-servidor
 * @param realm realm do Keycloak
 * @param clientId client ID OAuth
 * @param clientSecret client secret OAuth (nunca versionar)
 * @param timeout tempo máximo de cada chamada ao endpoint de token
 */
@Validated
@ConfigurationProperties(prefix = "liminallabs.gateway.auth.keycloak")
public record KeycloakProperties(
    @NotBlank String baseUrl,
    @NotBlank String baseUrlInternal,
    @NotBlank String realm,
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    @DefaultValue("5s") Duration timeout
) {
}
