# Design

## Context

Ver `proposal.md` (Why). Estado atual: `TokenStore` (aplicação) depende de `TokenDocument`/`TokenRepository` (Mongo síncrono) e mantém um `ConcurrentHashMap` como cache; `CustomBearerAuthFilter` chama Mongo e Keycloak de forma bloqueante dentro do `GlobalFilter`; `KeycloakAuthProvider` usa `RestClient` criado a cada chamada; `Token` é mutável e carrega anotações Jackson do formato do Keycloak. O gateway roda em Kubernetes com mais de uma réplica.

## Goals / Non-Goals

**Goals:**
- Nenhuma chamada bloqueante no event loop.
- Camadas respeitando a regra de dependência: `transport` → `application` → `domain`; `infra` implementa portas da `application`.
- Comportamento correto com várias réplicas sem coordenação distribuída.

**Non-Goals:**
- Validação de `id_token`/`nonce` (o gateway não consome `id_token`).
- Logout explícito / revogação no Keycloak.
- Lock distribuído de refresh entre réplicas.
- Limpar o cookie de sessão do navegador quando a sessão é encerrada no servidor.

## Decisions

### Stack reativa de ponta a ponta
`ReactiveMongoRepository` + `WebClient`; todas as portas devolvem `Mono`.
- *Alternativa:* manter APIs síncronas e envolver com `subscribeOn(boundedElastic())`. Mais simples, mas consome threads do pool elástico por requisição e deixa o single-flight mais difícil de compor. Como o gateway é WebFlux, a stack reativa é o caminho natural.

### Sem cache em memória
O Mongo passa a ser a fonte única. Busca por `_id` custa ~1 ms e elimina inconsistência entre pods após um refresh.
- *Alternativa:* Caffeine com TTL curto. Reduz a latência, mas reintroduz o token velho em outras réplicas durante a janela do TTL, que é exatamente o problema a ser resolvido.

### Single-flight local + recuperação otimista entre réplicas
`SessionService` mantém `ConcurrentMap<UUID, Mono<Token>>` com `.cache()` para o refresh em andamento; a entrada sai do mapa quando o refresh termina. Entre réplicas não há lock: quando o provider responde `invalid_grant`, o serviço relê a sessão; se ela já tiver outro token com access token válido (outra réplica renovou), usa esse token em vez de apagar a sessão.
- *Alternativa:* lock distribuído no Mongo (`findAndModify` com lease). Mais forte, porém mais complexo; com a configuração padrão do Keycloak (sem *revoke refresh token*) refreshes concorrentes já são aceitos.

### Erros tipados do provider
`InvalidGrantException` (HTTP 400 com `error=invalid_grant`) encerra a sessão. Qualquer outra falha (rede, timeout, 5xx, `invalid_client`) vira `AuthProviderUnavailableException` → `503`, preservando a sessão. Configuração errada (`invalid_client`) também cai em `503`, para não derrubar todas as sessões por um erro de deploy. As chamadas ao provider têm timeout configurável (`liminallabs.gateway.auth.keycloak.timeout`, padrão 5s).

### Login iniciado pelo gateway (`/oauth/login`)
O `401` aponta para `GET /oauth/login?returnTo=…` em vez da URL do Keycloak. O `state` e o PKCE são gerados só quando o navegador navega de fato, o que evita que vários `401` paralelos sobrescrevam o cookie de login uns dos outros. O cookie de login (`<session-cookie-name>_LOGIN`) guarda `state.verifier.base64url(returnTo)`, é `SameSite=Lax` (o callback chega via navegação cross-site vinda do Keycloak, e `Strict` não seria enviado) e vale 10 minutos. A comparação de `state` é em tempo constante; o `returnTo` é revalidado no callback.
- *Alternativa:* guardar o login request no Mongo indexado pelo `state`. Sem cookie, perde a amarração ao navegador, que é a proteção contra *login CSRF*.
- A URL do endpoint vem de `liminallabs.gateway.login-url`; se ausente, é derivada de `oauth-callback-url` resolvendo `login` relativo a ela (`…/oauth/callback` → `…/oauth/login`).

### Modelo de domínio e persistência
- `Token` vira `record` com `issuedAt: Instant`; validade e expiração da sessão recebem `Instant now` (vindo de um `Clock` injetado).
- `KeycloakTokenResponse` (infra) faz o mapeamento `snake_case` → `Token`.
- `TokenDocument` usa um record embutido `StoredToken` com os **mesmos nomes de campo do documento antigo** (`accessToken`, `refreshToken`, `expiresIn`, `refreshExpiresIn`, `creationDateTime`), então as sessões já gravadas continuam legíveis (BSON Date → `Instant`).
- `expiresAt` passa a ser calculado a partir de `issuedAt` do token, e não do momento do save.

### Configuração
`GatewayProperties` e `KeycloakProperties` viram `record`s `@Validated`. `KeycloakProperties` só é registrada pela `KeycloakConfiguration` (`@ConditionalOnProperty`), mantendo o Strategy: outro provider não exige propriedades do Keycloak. Os metadados vêm do `spring-boot-configuration-processor`; o JSON manual fica só com a propriedade de bypass.

### Segredos
`application-questmaster.yml` importa `optional:file:./config/questmaster-secrets.yml` (no `.gitignore`), com um `questmaster-secrets.example.yml` versionado. Variáveis de ambiente continuam funcionando pelo relaxed binding.

## Risks / Trade-offs

- [Mongo em todo request autenticado] → índice por `_id`; monitorar latência p99. Se necessário, reintroduzir cache com TTL de poucos segundos.
- [Keycloak com *revoke refresh token* e duas réplicas renovando no mesmo instante] → a releitura após `invalid_grant` cobre o caso em que a outra réplica já salvou; se a gravação dela ainda não aconteceu, a sessão é encerrada (usuário refaz o login). Aceitável.
- [Dois logins simultâneos em abas diferentes] → o último cookie de login vence; a outra aba recebe `400` no callback, e o usuário já estará logado pela primeira.
- [Contrato do `redirectUrl` muda de domínio (Keycloak → gateway)] → transparente para um frontend que apenas navega para a URL; frontends que inspecionam a URL precisam ser revisados.
- [Keycloak indisponível] → usuários com access token expirado recebem `503` até o provider voltar, inclusive em rotas que não exigiriam autenticação.

## Migration Plan

1. Criar `config/questmaster-secrets.yml` local (ou variáveis de ambiente) antes de subir.
2. Rotacionar o client secret do Keycloak (o antigo está no histórico do git).
3. Deploy normal; sessões existentes continuam válidas. Logins em andamento no momento do deploy falham com `400` (sem cookie de login) e basta tentar de novo.
4. Rollback: a versão anterior lê os documentos novos (mesmos nomes de campo em `token`).
