# Tasks

## 1. Build e configuração

- [x] 1.1 Trocar `spring-boot-starter-data-mongodb` pelo reativo e adicionar `spring-boot-starter-validation` e `spring-boot-configuration-processor`; verificar com `./gradlew compileJava`
- [x] 1.2 Converter `GatewayCustomProperties` em `config/GatewayProperties` (record `@Validated`) com `login-url` derivável, e criar `GatewayConfiguration` com o bean `Clock`; verificar que os metadados gerados contêm as propriedades
- [x] 1.3 Mover segredos de `application-questmaster.yml` para `config/questmaster-secrets.yml` (ignorado) com exemplo versionado; verificar com `git check-ignore`
- [x] 1.4 Reduzir `additional-spring-configuration-metadata.json` à propriedade de bypass

## 2. Domínio e porta de sessão

- [x] 2.1 Reescrever `Token` como record imutável com `issuedAt: Instant` e regras de validade/expiração baseadas em `Instant now`; verificar com `TokenTest`
- [x] 2.2 Criar a porta `SessionRepository` e o adapter `MongoSessionRepository` (reativo) com `TokenDocument`/`StoredToken` compatíveis com os documentos antigos; verificar com `MongoSessionRepositoryTest` e `TokenDocumentIndexTest`

## 3. Provider de autenticação

- [x] 3.1 Enxugar `AuthProvider` (reativo, PKCE, sem `validateToken`/`getProviderName`), criar `InvalidGrantException`, `AuthProviderUnavailableException` e `Pkce`; verificar com `PkceTest`
- [x] 3.2 Reescrever `KeycloakAuthProvider` com `WebClient` único, DTO `KeycloakTokenResponse`, timeout e mapeamento de erros; registrar via `KeycloakConfiguration`; verificar com `KeycloakAuthProviderTest` (invalid_grant, 5xx, invalid_client, PKCE)

## 4. Serviço de sessão

- [x] 4.1 Criar `SessionService` (create/update/remove/resolve) sem cache, com single-flight de refresh e recuperação após `invalid_grant`; remover `TokenStore`; verificar com `SessionServiceTest` (refresh concorrente, réplica, erro transitório, fallback de refresh token)

## 5. Transporte

- [x] 5.1 Criar `ReturnPath`, `LoginRequest` e `SessionCookies`; verificar com `LoginRequestTest`
- [x] 5.2 Mover o filtro para `token/transport`, torná-lo reativo, sem Bearer vazio, com `503` em falha transitória e JSON via Jackson apontando para `/oauth/login`; verificar com `CustomBearerAuthFilterTest`
- [x] 5.3 Substituir `OauthCallbackController` por `OauthController` com `/oauth/login` e `/oauth/callback` validando `state` e PKCE; verificar com `OauthControllerTest`
- [x] 5.4 Adaptar `DevTokenBypassController` ao novo modelo com DTO próprio; verificar com `./gradlew compileJava`

## 6. Integração e documentação

- [x] 6.1 Rodar `./gradlew test` completo e `openspec validate harden-session-gateway --strict`
- [x] 6.2 Atualizar o README (arquitetura, fluxo de login, configuração, segredos, limitações)

## Workflow follow-up

- Rotacionar o client secret do Keycloak `questmaster`.
- Arquivar a change com `/opsx:archive` após revisão.
