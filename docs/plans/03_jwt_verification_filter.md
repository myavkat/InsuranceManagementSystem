# Plan 03: JWT Verification Filter

> **Status:** Not started
> **Branch:** `phase4-api-gateway`
> **Depends on:** Plan 02 (API Gateway Core) — GatewayConfig.java and application.yml must exist
> **Blocks:** Plan 06 (Integration Tests)

## Objective

Implement a global JWT authentication filter in the API Gateway that:
- Intercepts all requests EXCEPT whitelisted public paths
- Extracts JWT from `Authorization: Bearer <token>` header
- Validates JWT locally using an RSA public key (cached, with periodic refresh capability)
- On success: injects `X-User-Id` and `X-User-Roles` headers into the downstream request
- On failure: returns `401` with standardized `ErrorResponse` JSON body

## Files to Read Before Starting

| File | Purpose |
|------|---------|
| `docs/outlines/06_API_GATEWAY_AUTH.md` | JWT format (RSA-256, claims: sub, roles, iat, exp, jti), public key endpoint, filter chain order |
| `docs/outlines/01_SYSTEM_ARCHITECTURE.md` | Auth service role, Gateway filtering |
| `services/api-gateway/src/main/resources/application.yml` | Current routes, `metadata.auth-required` flags |
| `services/api-gateway/src/main/java/.../config/GatewayConfig.java` | Where to register the JWT filter bean |
| `services/api-gateway/src/main/java/.../dto/ErrorResponse.java` | Error response format to use |
| `services/api-gateway/build.gradle.kts` | JJWT library already included |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Lombok order, Jackson imports |

## Technical Context (Inline)

### JWT Specification (from `06_API_GATEWAY_AUTH.md`)
- **Algorithm:** RSA-256 (`RS256`)
- **Claims:** `sub` (userId), `roles` (array of strings), `iat`, `exp`, `jti`
- **Access Token expiry:** 15 minutes
- **Header:** `Authorization: Bearer <token>`
- **Public key location for dev:** The Auth Service is a stub. For now, embed a dev RSA key pair as a classpath resource and load the public key from there. In production, the key will be fetched from `GET /api/auth/public-key`.

### Whitelisted (Public) Paths
These paths bypass JWT validation entirely:
- `POST /api/auth/login`
- `POST /api/auth/register`
- `POST /api/auth/refresh`
- `GET /api/reference-data/**` (per route table — public read-only)

These are determined at runtime by checking the route's `metadata.auth-required` flag (set in `application.yml`).

### Header Injection
On successful validation, add these headers to the downstream request:
- `X-User-Id` — value of `sub` claim (UUID string)
- `X-User-Roles` — comma-separated roles from `roles` claim

### Error Responses
- Missing/invalid `Authorization` header → `401` with `{"success":false,"message":"Missing or invalid Authorization header",...}`
- Expired token → `401` with `{"success":false,"message":"Token expired",...}`
- Invalid signature → `401` with `{"success":false,"message":"Invalid token signature",...}`
- Malformed token → `401` with `{"success":false,"message":"Malformed token",...}`

### Filter Execution Order
JWT filter must execute AFTER rate limiter but BEFORE route forwarding. Use `@Order(-1)` or `Ordered.HIGHEST_PRECEDENCE + 100` to place it appropriately.

### Reactive Filter Pattern
Spring Cloud Gateway uses reactive `GlobalFilter` + `Ordered`:
```java
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // check path → skip or validate → chain.filter() or return 401
    }

    @Override
    public int getOrder() {
        return -1; // early in chain, after rate limiter
    }
}
```

### Key Loading Strategy (Dev Mode)
1. Check `application.yml` for `gateway.auth.public-key` property (Base64-encoded PEM)
2. If not set, fall back to classpath resource `keys/public-key.pem`
3. A scheduled task refreshes the key every 60 minutes from the Auth Service (configurable; no-op in dev since Auth Service is a stub)

### Generating Dev RSA Key Pair
Use `openssl` to generate a key pair for development:
```bash
openssl genrsa -out private-key.pem 2048
openssl rsa -in private-key.pem -pubout -out public-key.pem
```

The public key PEM is committed to the repo; the private key is NOT committed (it would go to the Auth Service, which doesn't exist yet). For Gateway testing, we'll generate a self-contained key pair, embed only the public key, and use a pre-signed test JWT for integration tests in Plan 06.

---

## Steps

### Step 1: Generate dev RSA key pair and create classpath resource

Generate the key pair (using Git Bash or WSL):

```bash
cd services/api-gateway/src/main/resources
mkdir -p keys
openssl genrsa -out keys/private-key.pem 2048
openssl rsa -in keys/private-key.pem -pubout -out keys/public-key.pem
```

**IMPORTANT:** Add `private-key.pem` to `.gitignore`:
In `services/api-gateway/.gitignore` (create if doesn't exist):
```
keys/private-key.pem
```

The `public-key.pem` IS committed — it's needed by the Gateway at runtime.

### Step 2: Create `JwtPublicKeyProvider.java`

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/auth/JwtPublicKeyProvider.java`

```java
package com.insurancemanagementsystem.gateway.auth;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RSA public key for JWT verification.
 *
 * In development: reads from classpath keys/public-key.pem.
 * In production: will be fetched from Auth Service's /api/auth/public-key endpoint
 * and refreshed periodically.
 */
@Component
@Slf4j
public class JwtPublicKeyProvider {

    private volatile PublicKey cachedPublicKey;

    @Value("${gateway.auth.public-key-location:classpath:keys/public-key.pem}")
    private String publicKeyLocation;

    /**
     * Returns the cached public key, loading it on first call.
     * Thread-safe via volatile read + synchronized load.
     */
    public PublicKey getPublicKey() {
        if (cachedPublicKey == null) {
            synchronized (this) {
                if (cachedPublicKey == null) {
                    cachedPublicKey = loadPublicKey();
                }
            }
        }
        return cachedPublicKey;
    }

    private PublicKey loadPublicKey() {
        try {
            // Read PEM from classpath
            ClassPathResource resource = new ClassPathResource("keys/public-key.pem");
            String pemContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Strip PEM headers/footers
            String base64Key = pemContent
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey key = keyFactory.generatePublic(spec);
            log.info("JWT public key loaded successfully from classpath:keys/public-key.pem");
            return key;
        } catch (Exception e) {
            log.error("Failed to load JWT public key", e);
            throw new IllegalStateException("Cannot load JWT public key — gateway cannot validate tokens", e);
        }
    }
}
```

### Step 3: Create `JwtAuthFilter.java`

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/auth/JwtAuthFilter.java`

```java
package com.insurancemanagementsystem.gateway.auth;

import com.insurancemanagementsystem.gateway.dto.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Global filter that validates JWT tokens on authenticated routes.
 *
 * Whitelisted routes (metadata.auth-required=false in application.yml) are skipped.
 * On success, injects X-User-Id and X-User-Roles headers into downstream request.
 * On failure, returns 401 with standardized ErrorResponse JSON.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtPublicKeyProvider keyProvider;

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);

        // 1. Check if route requires auth
        if (route == null || !isAuthRequired(route)) {
            return chain.filter(exchange);
        }

        // 2. Extract Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        // 3. Validate token
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keyProvider.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 4. Extract claims
            String userId = claims.getSubject();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            String rolesHeader = roles != null
                    ? String.join(",", roles)
                    : "";

            // 5. Inject headers into downstream request
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Roles", rolesHeader)
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

            log.debug("JWT validated for user={}, roles={}", userId, rolesHeader);
            return chain.filter(mutatedExchange);

        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            return unauthorized(exchange, "Token expired");
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return unauthorized(exchange, "Invalid token signature");
        } catch (Exception e) {
            log.error("Unexpected JWT validation error", e);
            return unauthorized(exchange, "Authentication failed");
        }
    }

    @Override
    public int getOrder() {
        // Execute early but after rate limiter (which uses -1 by default in Spring Cloud Gateway)
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    /**
     * Checks the route metadata to determine if auth is required.
     * Routes without explicit metadata.auth-required are treated as requiring auth (secure by default).
     */
    private boolean isAuthRequired(Route route) {
        Object authRequired = route.getMetadata().get("auth-required");
        if (authRequired instanceof Boolean b) {
            return b;
        }
        // Secure by default: if metadata key is missing, require auth
        return true;
    }

    /**
     * Writes a 401 JSON error response and short-circuits the filter chain.
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse error = ErrorResponse.of(message);
        byte[] bytes;
        try {
            // Manual JSON serialization to avoid ObjectMapper dependency
            String json = String.format(
                    "{\"success\":false,\"message\":\"%s\",\"data\":null,\"timestamp\":\"%s\"}",
                    escapeJson(message),
                    java.time.Instant.now().toString()
            );
            bytes = json.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            bytes = "{\"success\":false,\"message\":\"Internal error\"}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Minimal JSON string escaping for error messages.
     */
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
```

**IMPORTANT:** The `unauthorized()` method manually constructs JSON instead of using an `ObjectMapper`. This avoids introducing `tools.jackson.databind.ObjectMapper` into the Gateway project (which would create a Jackson 2/3 conflict with what Spring Cloud Gateway already uses internally). The error messages are known, controlled strings — manual JSON is safe here.

### Step 4: Add JWT configuration to `application.yml`

Add the following to `services/api-gateway/src/main/resources/application.yml` (at the end, before `logging:`):

```yaml
# JWT Authentication
gateway:
  auth:
    public-key-location: classpath:keys/public-key.pem
    key-refresh-interval-minutes: 60   # how often to re-fetch from Auth Service (future)
```

### Step 5: Create JWT utility for test token generation (test-only)

**File:** `services/api-gateway/src/test/java/com/insurancemanagementsystem/gateway/auth/TestJwtTokenGenerator.java`

This utility class generates test JWTs signed with the dev private key. It's for use in Plan 06 (integration tests) and manual testing.

```java
package com.insurancemanagementsystem.gateway.auth;

import io.jsonwebtoken.Jwts;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Test utility for generating signed JWTs using the dev private key.
 * USAGE: Only in test scope. Used by Plan 06 integration tests.
 */
public class TestJwtTokenGenerator {

    private static volatile PrivateKey cachedPrivateKey;

    public static PrivateKey getPrivateKey() {
        if (cachedPrivateKey == null) {
            synchronized (TestJwtTokenGenerator.class) {
                if (cachedPrivateKey == null) {
                    cachedPrivateKey = loadPrivateKey();
                }
            }
        }
        return cachedPrivateKey;
    }

    private static PrivateKey loadPrivateKey() {
        try {
            ClassPathResource resource = new ClassPathResource("keys/private-key.pem");
            String pemContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String base64Key = pemContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test private key", e);
        }
    }

    /**
     * Creates a valid JWT with the given claims, signed with the dev private key.
     */
    public static String createToken(UUID userId, List<String> roles, Instant expiration) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration))
                .id(UUID.randomUUID().toString())
                .signWith(getPrivateKey())
                .compact();
    }

    /**
     * Creates a valid JWT expiring in 15 minutes (standard access token lifetime).
     */
    public static String createValidToken(UUID userId, List<String> roles) {
        return createToken(userId, roles, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    /**
     * Creates an expired JWT (for testing 401 on expiry).
     */
    public static String createExpiredToken(UUID userId, List<String> roles) {
        return createToken(userId, roles, Instant.now().minus(1, ChronoUnit.MINUTES));
    }
}
```

### Step 6: Wire JwtAuthFilter in GatewayConfig

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/config/GatewayConfig.java`

The `JwtAuthFilter` is auto-detected via `@Component` — no explicit bean declaration needed. However, to document the filter chain order explicitly, add a comment block in `GatewayConfig.java`:

```java
/**
 * Filter chain order (defined by @Order on each GlobalFilter):
 * 1. RateLimiter (built-in Spring Cloud Gateway, order = -1) — Plan 04
 * 2. JwtAuthFilter (HIGHEST_PRECEDENCE + 100 = Integer.MIN_VALUE + 100) — Plan 03
 * 3. Route forwarding (built-in)
 * 4. Response filters (Plan 05)
 */
```

Place this comment at the top of the class.

### Step 7: Update `.gitignore`

**File:** `services/api-gateway/.gitignore` (create if doesn't exist)

```
keys/private-key.pem
```

Also verify the root `.gitignore` doesn't already exclude `.pem` files. If the root `.gitignore` has a global exclude for `.pem`, remove it or make it more specific — `public-key.pem` must be committed.

### Step 8: Build & Verify

```bash
.\gradlew.bat :services:api-gateway:build
```

---

## Acceptance Criteria

- [ ] `JwtPublicKeyProvider.java` compiles and loads public key from classpath
- [ ] `JwtAuthFilter.java` compiles and implements `GlobalFilter, Ordered`
- [ ] `TestJwtTokenGenerator.java` compiles (test scope)
- [ ] Dev RSA key pair generated: `keys/public-key.pem` (committed), `keys/private-key.pem` (gitignored)
- [ ] `application.yml` has `gateway.auth.*` configuration block
- [ ] `.\gradlew.bat :services:api-gateway:build` passes
- [ ] `JwtAuthFilter` uses `@Component` (auto-detected by Spring)
- [ ] Filter short-circuits to 401 on missing/invalid/expired token
- [ ] Filter injects `X-User-Id` and `X-User-Roles` headers on valid token
- [ ] Routes with `metadata.auth-required: false` bypass JWT validation
- [ ] Manual JSON serialization in `unauthorized()` properly escapes special characters
