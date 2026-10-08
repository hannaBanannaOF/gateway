# Gateway

HTTP API gateway for the LiminalLabs platform, built with **Spring Cloud Gateway (WebFlux)** and **Java 21**. It handles request proxying, session-based OAuth 2.0 authentication with Keycloak, and token lifecycle management — all on top of a fully reactive stack.

## Overview

The gateway sits in front of all backend services and is responsible for:

- **Routing** — proxying incoming HTTP requests to the correct upstream service.
- **Authentication** — intercepting every request via a global filter that resolves a session cookie into a bearer token and injects `Authorization: Bearer <access_token>` before forwarding.
- **Token lifecycle** — transparently refreshing expired access tokens using the stored refresh token.
- **OAuth 2.0 callback** — handling the redirect from Keycloak after login, exchanging the authorization code for tokens, and setting a session cookie on the browser.

> Routes are currently defined statically in YAML files. To update routes on the fly, use the Spring Cloud Gateway Admin API to update routes, or restart the gateway to apply changes from the YAML files.

---

## Architecture

```
Browser / Client
      │
      ▼
┌───────────────────────────────────────────────┐
│               Gateway (port 8081)             │
│                                               │
│  token/transport                              │
│    CustomBearerAuthFilter (GlobalFilter)      │
│      ├─ session cookie → SessionService       │
│      ├─ injects Authorization: Bearer         │
│      └─ upstream 401 → {redirectUrl: login}   │
│    OauthController                            │
│      ├─ GET /oauth/login     (state + PKCE)   │
│      └─ GET /oauth/callback  (code → session) │
│    DevTokenBypassController  (dev only)       │
│                                               │
│  token/application                            │
│    SessionService  (refresh, single-flight)   │
│    SessionRepository  ◄── port                │
│                                               │
│  token/infra/mongo   MongoSessionRepository   │
│  auth_provider/infra/kc  KeycloakAuthProvider │
└───────────────────────────────────────────────┘
      │                        │
      ▼                        ▼
 Keycloak                 MongoDB
 (OAuth 2.0)        (sessions, TTL index)
```

The code follows Ports & Adapters: `transport` → `application` → `domain`, and `infra` implements the ports declared in `application` (`SessionRepository`, `AuthProvider`). The whole request path is reactive (reactive MongoDB driver + `WebClient`), so nothing blocks the Netty event loop.

---

## Key Components

### `CustomBearerAuthFilter`
**`token/transport/CustomBearerAuthFilter.java`** — A `GlobalFilter` applied to every routed request.

1. Reads the session cookie and parses it as a `UUID` (malformed values are ignored).
2. Asks `SessionService` for a token with a valid access token. If there is one, injects `Authorization: Bearer <access_token>` and strips the `Cookie` header.
3. If the session is unknown, expired, or its refresh was rejected, the request is forwarded **without** `Authorization` (never an empty bearer).
4. If the auth provider is unavailable while refreshing, answers `503` without calling the upstream and keeps the session.
5. If the upstream answers `401`, replaces the body with `{"redirectUrl": "<gateway>/oauth/login?returnTo=<Original-Url>"}`. `returnTo` is only included when `Original-Url` is a safe relative path.

### `SessionService`
**`token/application/SessionService.java`** — Session lifecycle. MongoDB is the single source of truth (no in-memory cache, so every replica sees a refreshed token immediately).

- `resolve` refreshes an expired access token once per session at a time (concurrent requests share the same refresh).
- When the provider rejects the refresh (`invalid_grant`), it re-reads the session: if another replica already stored a newer valid token, that token is used; otherwise the session is deleted.
- Transient provider failures propagate as `AuthProviderUnavailableException` and never delete the session.

### `Token`
**`token/domain/Token.java`** — Immutable record (`accessToken`, `refreshToken`, `expiresIn`, `refreshExpiresIn`, `issuedAt`). Validity checks take the current `Instant` (from an injected `Clock`). The session expires when both tokens are expired; tokens without any lifetime (e.g. Keycloak offline tokens) never expire.

### `MongoSessionRepository`
**`token/infra/mongo/`** — Stores sessions in the `tokens` collection. `expiresAt` has a TTL index (`expireAfter=0s`), so MongoDB deletes dead sessions on its own. The stored field names are the same as before, so existing sessions stay readable.

### `AuthProvider` / `KeycloakAuthProvider`
**`auth_provider/application/AuthProvider.java`** — Port for the identity provider:

```java
String authorizationUrl(String redirectUri, String state, String codeChallenge);
Mono<Token> exchangeCode(String code, String redirectUri, String codeVerifier);
Mono<Token> refresh(String refreshToken);
```

Failures are typed: `InvalidGrantException` (HTTP 400 `invalid_grant`, the grant itself is dead) or `AuthProviderUnavailableException` (network, timeout, 5xx, `invalid_client` and anything else).

**`auth_provider/infra/kc/KeycloakAuthProvider.java`** — Keycloak implementation using a single `WebClient` with a configurable timeout, registered by `KeycloakConfiguration` when:
```yaml
liminallabs.gateway.auth.provider-name: keycloak
```

### `OauthController`
**`token/transport/OauthController.java`** — OAuth 2.0 Authorization Code flow with `state` and PKCE (S256):

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/oauth/login?returnTo=/path` | Generates a random `state` and a PKCE pair, stores them (plus `returnTo`) in the short-lived `<session-cookie-name>_LOGIN` cookie (`HttpOnly`, `SameSite=Lax`, 10 min) and redirects to Keycloak. |
| `GET`  | `/oauth/callback` | Checks `state` against the login cookie (`400` if missing or different), exchanges the `code` with the `code_verifier`, creates the session, sets the session cookie, clears the login cookie and redirects (`302`) to `frontend-url` + `returnTo`. Rejected code → `400`; provider down → `503`. |

### `DevTokenBypassController`
**`token/transport/DevTokenBypassController.java`** — only exists when `liminallabs.gateway.security.allow-token-bypass=true`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/oauth/bypass` | Body in OAuth token format (`access_token`, `refresh_token`, `expires_in`, `refresh_expires_in`). Creates a session and returns its UUID. |
| `PUT`  | `/oauth/bypass/{id}` | Replaces the token of an existing session. |

### `GatewayProperties`
**`config/GatewayProperties.java`** — Validated record bound to `liminallabs.gateway`; startup fails if a required property is missing.

| Property | Description |
|----------|-------------|
| `session-cookie-name` | Name of the cookie carrying the session UUID (required) |
| `oauth-callback-url` | Full callback URL registered in Keycloak (required) |
| `login-url` | Public URL of `/oauth/login`. Optional: derived from `oauth-callback-url` (`…/oauth/callback` → `…/oauth/login`) |
| `frontend-url` | Base URL the browser is redirected to after login (required) |
| `cookie-domain` | Domain attribute of the session cookie (omitted when blank) |
| `cookie-same-site` | `SameSite` attribute (`Strict` by default) |
| `cookie-secure` | Whether to set the `Secure` flag (`false` by default) |
| `cookie-max-age` | Cookie max-age in seconds (default: `3600`) |
| `auth.provider-name` | Which `AuthProvider` to activate (`keycloak`, required) |
| `auth.keycloak.base-url` | Public Keycloak base URL (used to build the login redirect) |
| `auth.keycloak.base-url-internal` | Internal Keycloak URL (used for server-to-server token calls) |
| `auth.keycloak.realm` | Keycloak realm name |
| `auth.keycloak.client-id` | OAuth client ID |
| `auth.keycloak.client-secret` | OAuth client secret (**never commit it**) |
| `auth.keycloak.timeout` | Timeout of each token call (default: `5s`) |

Configuration metadata is generated by `spring-boot-configuration-processor`, so IDEs autocomplete and document these properties.

---

## Static Routes

Routes are defined in `config/questmaster-routes.yml` and loaded as a Spring Cloud Gateway YAML config:

| Route ID | Path Predicate | Upstream |
|----------|---------------|----------|
| `questmaster-core` | `/core/api/**` | `http://questmaster-core:8080` |
| `questmaster-coc`  | `/coc/api/**`  | `http://questmaster-coc:8080`  |

To mount this file at startup, include it via `spring.config.import` or pass it with `--spring.config.additional-location`.

---

## OAuth 2.0 Login Flow

```
Browser                     Gateway                    Keycloak
  │                            │                           │
  │── GET /core/api/... ──────►│── forward to upstream     │
  │                            │◄── 401 Unauthorized       │
  │◄── 401 {redirectUrl:       │                           │
  │     /oauth/login?returnTo} │                           │
  │                            │                           │
  │── GET /oauth/login ───────►│ state + PKCE              │
  │◄── 302 + login cookie ─────│                           │
  │──────────── GET /auth?state&code_challenge ───────────►│
  │◄────────────── 302 /oauth/callback?code&state ─────────│
  │                            │                           │
  │── GET /oauth/callback ────►│ check state vs cookie     │
  │                            │── POST /token ───────────►│
  │                            │   (code + code_verifier)  │
  │                            │◄── { access_token, ... }──│
  │                            │ store session in MongoDB  │
  │◄── 302 + Set-Cookie ───────│                           │
  │                            │                           │
  │── GET /core/api/... ──────►│ cookie → Bearer           │
  │   (with session cookie)    │── forward + Bearer ──────►│
```

The frontend contract is unchanged: on a `401` it just navigates to `redirectUrl`.

---

## Running Locally

### Prerequisites

- Java 21
- MongoDB (default: `localhost:27018`, database: `gateway`)
- Keycloak (default: `localhost:8080`)

### Secrets

Secrets are not versioned. The `questmaster` profile imports `config/questmaster-secrets.yml` (git-ignored) when it exists:

```bash
cp config/questmaster-secrets.example.yml config/questmaster-secrets.yml
```

Then fill in the MongoDB password and the Keycloak client secret. Environment variables also work (e.g. `LIMINALLABS_GATEWAY_AUTH_KEYCLOAK_CLIENTSECRET`, `SPRING_DATA_MONGODB_PASSWORD`).

### Start with the `questmaster` profile

```bash
./gradlew bootRun --args='--spring.profiles.active=questmaster'
```

On Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=questmaster"
```

The service will start on **port 8081**.

### Load the questmaster routes

Pass the route config file as an additional location:

```bash
./gradlew bootRun --args='--spring.profiles.active=questmaster --spring.config.additional-location=file:./config/questmaster-routes.yml'
```

### Allow OAuth bypass

Enable OAuth bypass endpoints by setting the application property `liminallabs.gateway.security.allow-token-bypass` to `true`.

```bash
./gradlew bootRun --args='--spring.profiles.active=questmaster --spring.config.additional-location=file:./config/questmaster-routes.yml --liminallabs.gateway.security.allow-token-bypass=true'
```

---

## Configuration Reference

### `application.yml` (base)

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `spring.cloud.gateway.server.webflux.globalcors...allowedOrigins` | `${ALLOWED_ORIGIN}` | CORS allowed origin |

### `application-questmaster.yml` (local profile)

| Property | Value |
|----------|-------|
| `server.port` | `8081` |
| `server.max-http-request-header-size` | `10KB` |
| `spring.data.mongodb.host` | `localhost` |
| `spring.data.mongodb.port` | `27018` |
| `spring.data.mongodb.database` | `gateway` |
| `liminallabs.gateway.session-cookie-name` | `QUESTMASTER_SESSION` |
| `liminallabs.gateway.oauth-callback-url` | `http://localhost:8081/oauth/callback` |
| `liminallabs.gateway.frontend-url` | `http://localhost:3000` |
| `liminallabs.gateway.cookie-domain` | `localhost` |
| `liminallabs.gateway.cookie-same-site` | `Strict` |
| `liminallabs.gateway.cookie-secure` | `false` |
| `liminallabs.gateway.cookie-max-age` | `3600` |
| `liminallabs.gateway.auth.provider-name` | `keycloak` |
| `liminallabs.gateway.auth.keycloak.realm` | `LiminalLabs` |
| `liminallabs.gateway.auth.keycloak.client-id` | `questmaster` |
| `spring.config.import` | `optional:file:./config/questmaster-secrets.yml` |

### Dev only property

| Property | Description |
|----------|-------------|
| `liminallabs.gateway.security.allow-token-bypass` | `true` allows token bypass, `false` disables it. |

---

## Docker

The Dockerfile uses a two-stage build with `ubi8/openjdk-21` as both builder and runtime, running as user `185` (the default UBI non-root user).

Build the image:

```bash
docker build -t labs.liminal/gateway .
```

Run the container:

```bash
docker run --rm -p 8081:8081 \
  -e ALLOWED_ORIGIN=http://localhost:3000 \
  labs.liminal/gateway
```

> **Note:** When running in a container, make sure to override MongoDB and Keycloak connection properties either via environment variables or a mounted config file, since the `questmaster` profile points to `localhost`.

---

## Known Limitations & Notes

- **No access token introspection**: the gateway trusts the expiry timestamps returned by the provider; upstream services must validate the JWT themselves.
- **`id_token` / `nonce` are not validated**: the gateway does not consume the `id_token`.
- **Refresh coordination is per replica**: concurrent refreshes are collapsed inside one instance; across replicas the gateway recovers by re-reading the session after an `invalid_grant`.
- **Provider outage**: users whose access token expired get `503` until Keycloak is back (sessions are kept).
- **Logout** is not implemented: sessions end when tokens expire or a refresh is rejected.

---

## Tech Stack

| Technology | Version | Role |
|------------|---------|------|
| Java | 21 | Runtime |
| Spring Boot | 3.5.13 | Application framework |
| Spring Cloud Gateway | 2025.0.2 | Reactive HTTP routing |
| Spring Cloud Kubernetes | 2025.0.2 | Kubernetes service discovery |
| Spring Data MongoDB Reactive | — | Session persistence |
| Spring Boot Actuator | — | Health & management endpoints |
| Lombok | 1.18.34 | Boilerplate reduction |
| Keycloak | — | OAuth 2.0 / OpenID Connect provider |
