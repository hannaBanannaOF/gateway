# Gateway

This service is a HTTP gateway. It is built with Spring Cloud Gateway and routes requests dynamically based on entries stored in MongoDB.

In addition to request routing, the service:

- listens for route update events on RabbitMQ
- refreshes gateway routes without a restart
- integrates with Keycloak for OAuth login
- stores OAuth tokens in memory and injects the bearer token into proxied requests

## What the Service Does

At startup, and every time routes are refreshed, the gateway loads route definitions from MongoDB through `RouteLocatorService`.

Each route entry contains:

- `instance`: unique route identifier
- `regex`: path pattern used by Spring Cloud Gateway
- `uri`: destination service URI

Important: even though the field is named `regex`, the current implementation passes it to Spring Gateway's `path(...)` predicate. In practice, this means you should provide Spring path patterns such as `/api/orders/**`, not Java regular expressions.

When a message arrives on the configured RabbitMQ queue, the gateway:

1. creates or updates the route entry in MongoDB
2. publishes a `RefreshRoutesEvent`
3. rebuilds the active route list

## Authentication Flow

The gateway includes a global filter that handles OAuth session forwarding:

1. It looks for the configured session cookie.
2. If a token is found in the token store, it forwards the request with `Authorization: Bearer <access-token>`.
3. If the access token is expired but the refresh token is still valid, it tries to refresh the session against Keycloak.
4. If the downstream response is `401 Unauthorized`, the gateway returns a JSON payload containing a `redirectUrl` for the Keycloak login screen.
5. After Keycloak redirects back to `/oauth/callback`, the gateway exchanges the authorization code for tokens, stores them, and sets a session cookie.

Current behavior to be aware of:

- the OAuth callback currently writes the cookie with `domain=localhost`

## Local Configuration

The repository includes two configuration files:

- `src/main/resources/application.yml`: base config with the default port (`8080`) and CORS origin from `ALLOWED_ORIGIN`
- `src/main/resources/application-questmaster.yml`: a fuller local profile with MongoDB, RabbitMQ, Keycloak, Actuator, and local frontend settings

The `questmaster` profile currently uses:

- server port `8081`
- RabbitMQ on `localhost:5672`
- MongoDB on `localhost:27018`
- frontend URL `http://localhost:3000`
- OAuth callback URL `http://localhost:8081/oauth/callback`

## Required Dependencies

To run the gateway in a meaningful way, you will usually need:

- Java 21
- RabbitMQ
- MongoDB
- Keycloak

If you only need the app to start, you can provide your own Spring configuration and infrastructure values instead.

## Running the Service

Run with the local Questmaster profile:

```bash
./gradlew bootRun --args='--spring.profiles.active=questmaster'
```

On Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=questmaster"
```

## Route Update Message Format

The RabbitMQ listener expects a JSON payload shaped like this:

```json
{
  "instance": "orders-service",
  "regex": "/api/orders/**",
  "url": "http://localhost:8090"
}
```

That payload is stored as a MongoDB route document and then used to rebuild the gateway routes.

## Development-Only Endpoints

These endpoints are enabled only when `spring.profiles.active=dev`:

- `POST /oauth/bypass`
- `PUT /oauth/bypass/{id}`

They are intended for local development shortcuts and should not be relied on in shared or production environments.

## Main Configuration Properties

The application depends on these custom properties under `liminallabs.gateway`:

- `session_cookie_name`
- `oauth_callback_url`
- `frontend_url`
- `keycloak.base_url`
- `keycloak.base_url_internal`
- `keycloak.realm`
- `keycloak.client_id`
- `keycloak.client_secret`
- `amqp.queue`

It also requires the standard Spring connection properties for:

- `spring.rabbitmq.*`
- `spring.data.mongodb.*`
- `spring.cloud.gateway.globalcors.*`

## Docker

Build the image:

```bash
docker build -t labs.liminal/gateway .
```

Run the container:

```bash
docker run --rm -p 8080:8080 labs.liminal/gateway
```
