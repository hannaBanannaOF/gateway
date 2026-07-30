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
┌─────────────────────────────────────────┐
│            Gateway (port 8081)          │
│                                         │
│  CustomBearerAuthFilter (GlobalFilter)  │
│    ├─ reads session cookie              │
│    ├─ resolves Token from TokenStore    │
│    ├─ refreshes token if expired        │
│    └─ injects Authorization header      │
│                                         │
│  OauthCallbackController                │
│    ├─ GET /oauth/callback  (login flow) │
│    ├─ POST /oauth/bypass   (dev only)   │
│    └─ PUT  /oauth/bypass/{id} (dev only)│
│                                         │
│  Spring Cloud Gateway Routes            │
│    ├─ /core/api/** → questmaster-core   │
│    └─ /coc/api/**  → questmaster-coc    │
└─────────────────────────────────────────┘
      │                        │
      ▼                        ▼
 Keycloak                 MongoDB
 (OAuth 2.0)          (token storage)
```

---

## Key Components

### `CustomBearerAuthFilter`
**`token/infra/CustomBearerAuthFilter.java`** — A `GlobalFilter` applied to every request.

1. Reads the configured session cookie from the request.
2. Parses the cookie value as a `UUID` and fetches the corresponding `Token` from `TokenStore`.
3. If the access token is still valid, injects `Authorization: Bearer <access_token>` and strips the `Cookie` header before forwarding.
4. If the access token is expired but the refresh token is still valid, calls `AuthProvider.refreshToken()`, updates the store, and forwards with the new token.
5. If the refresh also fails, clears the session and strips the access token.
6. After the chain executes, if the downstream service responds with `401 Unauthorized`, the filter intercepts the response and returns a JSON body containing a `redirectUrl` pointing to the Keycloak login page.

### `TokenStore`
**`token/application/TokenStore.java`** — Manages the token lifecycle with a two-layer storage strategy:

- **In-memory cache** (`ConcurrentHashMap<UUID, Token>`) for fast reads on hot paths.
- **MongoDB** (`tokens` collection via `TokenRepository`) as the persistent store and fallback.

Operations: `createTokenEntry`, `getToken`, `updateToken`, `removeTokens`, `clearAll`.

### `Token`
**`token/domain/Token.java`** — Domain model representing an OAuth token set. Maps JSON fields `access_token`, `refresh_token`, `expires_in`, and `refresh_expires_in` from the Keycloak response. Includes two validation helpers:
- `isAccessTokenValid()` — checks whether `creationDateTime + expiresIn` is still in the future.
- `isRefreshTokenValid()` — same check for the refresh token.

### `AuthProvider` / `KeycloakAuthProvider`
**`auth_provider/application/AuthProvider.java`** — Provider interface decoupling the auth logic from Keycloak specifics:

```java
String getAuthorizationUrl(String redirectUri, String state);
Token exchangeCodeForToken(String code, String redirectUri);
Token refreshToken(String refreshToken);
boolean validateToken(String token);
String getProviderName();
```

**`auth_provider/infra/KeycloakAuthProvider.java`** — Keycloak implementation, activated via:
```yaml
liminallabs.gateway.auth.provider-name: keycloak
```
Uses Spring's `RestClient` (synchronous, blocking) to talk to Keycloak's OpenID Connect token endpoint. Note: `validateToken` is a stub that always returns `true`.

### `OauthCallbackController`
**`token/transport/OauthCallbackController.java`** — Handles the OAuth 2.0 authorization code flow:

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/oauth/callback` | Receives `code` and `state` from Keycloak, exchanges the code for tokens, stores them, sets the session cookie, and redirects the browser to the frontend. |
| `POST` | `/oauth/bypass` | **depends on application property `liminallabs.gateway.security.allow-token-bypass`** Injects a token directly into the store; returns the session UUID. |
| `PUT`  | `/oauth/bypass/{id}` | **depends on application property `liminallabs.gateway.security.allow-token-bypass`** Updates an existing token entry. |

The `state` query parameter forwarded by Keycloak is used as the post-login redirect path appended to `frontendUrl`.

### `GatewayCustomProperties`
**`properties/domain/GatewayCustomProperties.java`** — Typed configuration bound to the `liminallabs.gateway` prefix:

| Property | Description |
|----------|-------------|
| `session-cookie-name` | Name of the cookie carrying the session UUID |
| `oauth-callback-url` | Full callback URL registered in Keycloak |
| `frontend-url` | Base URL the browser is redirected to after login |
| `cookie-domain` | Domain attribute of the session cookie |
| `cookie-same-site` | `SameSite` attribute (`Strict` by default) |
| `cookie-secure` | Whether to set the `Secure` flag |
| `cookie-max-age` | Cookie max-age in seconds (default: `3600`) |
| `auth.provider-name` | Which `AuthProvider` bean to activate (`keycloak`) |
| `auth.keycloak.base-url` | Public Keycloak base URL (used to build the login redirect) |
| `auth.keycloak.base-url-internal` | Internal Keycloak URL (used for server-to-server token calls) |
| `auth.keycloak.realm` | Keycloak realm name |
| `auth.keycloak.client-id` | OAuth client ID |
| `auth.keycloak.client-secret` | OAuth client secret |

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
  │── GET /core/api/... ──────►│                           │
  │                            │── forward to upstream ───►│
  │                            │◄── 401 Unauthorized ──────│
  │◄── { redirectUrl: ... } ───│                           │
  │                            │                           │
  │─────────────────────── redirect to Keycloak ──────────►│
  │                            │                           │
  │◄────────────── redirect to /oauth/callback?code=...────│
  │                            │                           │
  │── GET /oauth/callback ────►│                           │
  │                            │── POST /token (code) ────►│
  │                            │◄── { access_token, ... }──│
  │                            │                           │
  │                            │ store token in MongoDB     │
  │                            │ + in-memory cache          │
  │◄── 308 + Set-Cookie ───────│                           │
  │                            │                           │
  │── GET /core/api/... ──────►│                           │
  │   (with session cookie)    │ resolve token from store   │
  │                            │── forward + Bearer token ─►│
```

---

## Running Locally

### Prerequisites

- Java 21
- MongoDB (default: `localhost:27018`, database: `gateway`)
- Keycloak (default: `localhost:8080`)

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
| `spring.data.mongodb.username` | `root` |
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

- **`validateToken` is a stub** — `KeycloakAuthProvider.validateToken()` always returns `true`. Token validation relies entirely on the expiry timestamps embedded in the `Token` object itself.
- **Blocking HTTP inside a reactive pipeline** — `KeycloakAuthProvider` uses the synchronous `RestClient` to call Keycloak. The callback controller wraps this call with `Schedulers.boundedElastic()` to avoid blocking the event loop, but the filter does not — this is a potential blocking call on the reactive thread.

---

## Tech Stack

| Technology | Version | Role |
|------------|---------|------|
| Java | 21 | Runtime |
| Spring Boot | 3.5.13 | Application framework |
| Spring Cloud Gateway | 2025.0.2 | Reactive HTTP routing |
| Spring Cloud Kubernetes | 2025.0.2 | Kubernetes service discovery |
| Spring Data MongoDB | — | Token persistence |
| Spring Boot Actuator | — | Health & management endpoints |
| Lombok | 1.18.34 | Boilerplate reduction |
| Keycloak | — | OAuth 2.0 / OpenID Connect provider |
