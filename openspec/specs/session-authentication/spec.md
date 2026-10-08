# session-authentication Specification

## Purpose
Traduz o cookie de sessão opaco do navegador em um Bearer token para os serviços upstream, mantendo o token renovado e encerrando sessões que não podem mais ser renovadas.

## Requirements

### Requirement: Sessão válida vira Bearer token
O gateway SHALL, quando a requisição trouxer um cookie de sessão que identifica uma sessão existente e não expirada, encaminhar ao upstream o header `Authorization: Bearer <access_token>` e remover o header `Cookie`.

#### Scenario: Access token válido
- **WHEN** a requisição traz um cookie de sessão cuja sessão tem access token válido
- **THEN** o upstream recebe `Authorization: Bearer <access_token>` e nenhum header `Cookie`

#### Scenario: Requisição sem cookie de sessão
- **WHEN** a requisição não traz o cookie de sessão
- **THEN** o upstream recebe a requisição sem alterações

#### Scenario: Cookie malformado ou sessão desconhecida
- **WHEN** o cookie de sessão não é um identificador válido ou não corresponde a nenhuma sessão ativa
- **THEN** o upstream recebe a requisição sem header `Authorization`

### Requirement: Nunca encaminhar Bearer vazio
O gateway MUST NOT encaminhar um header `Authorization` sem token.

#### Scenario: Sessão encerrada durante a requisição
- **WHEN** a sessão é encerrada porque o refresh foi rejeitado
- **THEN** o upstream recebe a requisição sem header `Authorization`

### Requirement: Refresh transparente do access token
O gateway SHALL renovar o access token usando o refresh token quando o access token estiver expirado e o refresh token ainda for válido, persistindo o novo token na sessão antes de encaminhar.

#### Scenario: Refresh bem-sucedido
- **WHEN** o access token expirou e o provider aceita o refresh token
- **THEN** a sessão passa a guardar o novo token e o upstream recebe o novo access token

#### Scenario: Provider não devolve novo refresh token
- **WHEN** a resposta de refresh não traz refresh token
- **THEN** a sessão mantém o refresh token anterior

### Requirement: Um único refresh por sessão em paralelo
O gateway SHALL executar no máximo um refresh simultâneo por sessão em cada instância; requisições concorrentes da mesma sessão MUST reutilizar o resultado desse refresh.

#### Scenario: Requisições paralelas com token expirado
- **WHEN** várias requisições da mesma sessão chegam com o access token expirado ao mesmo tempo
- **THEN** o provider recebe uma única chamada de refresh e todas as requisições usam o mesmo novo token

### Requirement: Refresh rejeitado encerra a sessão
O gateway SHALL encerrar a sessão quando o provider rejeitar o refresh token (`invalid_grant`), exceto quando a sessão persistida já contiver um token válido diferente, gravado por outra instância.

#### Scenario: Refresh token revogado
- **WHEN** o provider responde `invalid_grant` e a sessão persistida ainda guarda o mesmo refresh token
- **THEN** a sessão é removida e a requisição segue sem `Authorization`

#### Scenario: Outra réplica já renovou
- **WHEN** o provider responde `invalid_grant` mas a sessão persistida já tem outro token com access token válido
- **THEN** a sessão é mantida e o upstream recebe o access token persistido

### Requirement: Falha transitória do provider preserva a sessão
O gateway SHALL responder `503 Service Unavailable` sem encaminhar ao upstream e MUST NOT remover a sessão quando o refresh falhar por indisponibilidade, timeout ou erro inesperado do provider.

#### Scenario: Keycloak fora do ar
- **WHEN** o refresh falha por erro de rede ou resposta 5xx do provider
- **THEN** o cliente recebe `503` e a sessão continua existindo

### Requirement: Expiração da sessão
A sessão SHALL expirar quando tanto o access token quanto o refresh token estiverem expirados; tokens sem nenhum prazo informado pelo provider MUST NOT expirar. Sessões expiradas MUST ser tratadas como inexistentes, mesmo antes de removidas fisicamente.

#### Scenario: Ambos os tokens expirados
- **WHEN** a sessão tem access e refresh tokens expirados
- **THEN** o gateway trata a sessão como inexistente

#### Scenario: Token offline sem prazo
- **WHEN** o provider não informa prazo para nenhum dos tokens
- **THEN** a sessão não expira

### Requirement: Resposta 401 indica onde fazer login
O gateway SHALL substituir o corpo de uma resposta `401` do upstream por um JSON `{"redirectUrl": "<url>"}`, em que a URL aponta para o endpoint de login do gateway com o caminho original (header `Original-Url`) como `returnTo` quando ele for um caminho relativo seguro.

#### Scenario: Upstream responde 401
- **WHEN** o upstream responde `401` e a requisição tinha `Original-Url: /campaigns`
- **THEN** o cliente recebe `401` com `Content-Type: application/json` e `redirectUrl` igual à URL de login com `returnTo=/campaigns`

#### Scenario: Original-Url inseguro
- **WHEN** o header `Original-Url` não é um caminho relativo seguro
- **THEN** a `redirectUrl` aponta para o endpoint de login sem `returnTo`
