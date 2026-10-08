# Proposal

## Why

O gateway implementa o padrão BFF/Token Handler, mas a revisão de arquitetura encontrou falhas que aparecem em produção com carga e réplicas: I/O bloqueante no event loop do Netty, refresh concorrente que derruba sessões válidas, cache local que fica inconsistente entre pods, erros transitórios do Keycloak tratados como logout e um fluxo OAuth sem proteção CSRF (`state`) nem PKCE. Além disso, a camada de aplicação depende da infra e há segredos versionados.

## What Changes

- Pipeline de autenticação 100% reativo (Mongo reativo + `WebClient`), sem chamadas bloqueantes no event loop.
- Refresh de token com *single-flight* por sessão e recuperação quando outra réplica já fez o refresh.
- Remoção do cache em memória: o Mongo passa a ser a única fonte de verdade da sessão.
- Distinção entre refresh rejeitado (`invalid_grant`, encerra a sessão) e falha transitória do provider (mantém a sessão e responde `503`).
- **BREAKING (contrato interno):** o `redirectUrl` devolvido no `401` passa a apontar para o novo endpoint `GET /oauth/login?returnTo=...` do gateway, que gera `state` aleatório e PKCE (S256) e redireciona ao Keycloak. O frontend continua apenas navegando para `redirectUrl`.
- **BREAKING:** `GET /oauth/callback` valida o `state` contra um cookie de login de curta duração; `state` ausente ou divergente responde `400`.
- Corpo JSON do `401` serializado com Jackson (sem concatenação de string).
- Nunca encaminha `Authorization: Bearer ` vazio.
- Ports & Adapters corrigido: porta `SessionRepository` na aplicação; DTO do Keycloak isolado na infra; `Token` vira `record` imutável com `Instant`/`Clock`.
- Interface `AuthProvider` enxuta (remove `validateToken` e `getProviderName`).
- Injeção por construtor em todo o código; `WebClient` criado uma vez.
- Configuração tipada como `record` validado (`@Validated`), metadados gerados pelo `spring-boot-configuration-processor`.
- **BREAKING (dev):** segredos saem de `application-questmaster.yml` para um arquivo local ignorado pelo git ou variáveis de ambiente.

## Capabilities

### New Capabilities
- `session-authentication`: resolução do cookie de sessão em Bearer token, ciclo de vida e refresh da sessão, comportamento diante de falhas do provider.
- `oauth-login`: início do login, `state`/PKCE, callback, criação do cookie de sessão e redirecionamento seguro de volta ao frontend.

### Modified Capabilities
<!-- Nenhuma: o projeto ainda não tinha specs. -->

## Impact

- Código: todos os pacotes `token/*`, `auth_provider/*` e `properties/*` (movido para `config/`).
- Dependências: `spring-boot-starter-data-mongodb` → `spring-boot-starter-data-mongodb-reactive`; adiciona `spring-boot-starter-validation` e `spring-boot-configuration-processor`.
- API: novo `GET /oauth/login`; `GET /oauth/callback` passa a exigir o cookie de login.
- Frontend: nenhuma mudança obrigatória (continua seguindo `redirectUrl`).
- Operação: rotacionar o client secret do Keycloak, que ficou no histórico do git.
