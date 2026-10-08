# oauth-login Specification

## Purpose
Conduz o navegador pelo fluxo OAuth 2.0 Authorization Code com o provider de identidade, protegido por `state` e PKCE, e termina criando a sessão do gateway.

## Requirements

### Requirement: Início do login
O gateway SHALL expor `GET /oauth/login` que gera um `state` aleatório e um par PKCE (S256), guarda `state`, `code_verifier` e `returnTo` em um cookie de login `HttpOnly`, `SameSite=Lax`, com validade curta, e redireciona o navegador para a URL de autorização do provider.

#### Scenario: Login iniciado
- **WHEN** o navegador acessa `GET /oauth/login?returnTo=/campaigns`
- **THEN** recebe um redirect para o provider com `state`, `code_challenge` e `code_challenge_method=S256`, e um cookie de login contendo o mesmo `state`

#### Scenario: Cada login usa valores novos
- **WHEN** o login é iniciado duas vezes
- **THEN** os `state` e `code_challenge` gerados são diferentes

### Requirement: Callback valida o state
O callback `GET /oauth/callback` MUST responder `400` sem contatar o provider quando o `code` estiver ausente, quando não houver cookie de login ou quando o `state` recebido for diferente do guardado no cookie.

#### Scenario: State divergente
- **WHEN** o callback recebe um `state` diferente do cookie de login
- **THEN** a resposta é `400` e nenhuma sessão é criada

#### Scenario: Sem cookie de login
- **WHEN** o callback é chamado sem o cookie de login
- **THEN** a resposta é `400` e nenhuma sessão é criada

### Requirement: Callback cria a sessão
Com o `state` válido, o gateway SHALL trocar o `code` por tokens enviando o `code_verifier`, criar a sessão, definir o cookie de sessão (`HttpOnly`, `Path=/`, com `SameSite`, `Secure`, `Max-Age` e `Domain` configuráveis, omitindo `Domain` quando vazio), remover o cookie de login e redirecionar (`302`) para o frontend.

#### Scenario: Login concluído
- **WHEN** o callback recebe `code` e `state` válidos e o provider aceita a troca
- **THEN** o navegador recebe o cookie de sessão, o cookie de login expirado e um redirect para o frontend

#### Scenario: Code rejeitado pelo provider
- **WHEN** o provider rejeita o `code`
- **THEN** a resposta é `400` e nenhuma sessão é criada

#### Scenario: Provider indisponível na troca
- **WHEN** o provider está indisponível durante a troca do `code`
- **THEN** a resposta é `503` e nenhuma sessão é criada

### Requirement: Redirecionamento só para caminhos relativos
O gateway SHALL redirecionar após o login para `frontend-url` + `returnTo` apenas quando `returnTo` for um caminho relativo que começa com `/` e não com `//` nem `/\`; caso contrário MUST redirecionar para `frontend-url`.

#### Scenario: returnTo relativo
- **WHEN** o login foi iniciado com `returnTo=/campaigns/42`
- **THEN** o redirect final é `frontend-url/campaigns/42`

#### Scenario: Tentativa de open redirect
- **WHEN** o `returnTo` é `//evil.com`, `@evil.com`, `/\evil.com` ou uma URL absoluta
- **THEN** o redirect final é `frontend-url`
