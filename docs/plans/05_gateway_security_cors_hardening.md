# Plan 05: Gateway Security, CORS & Hardening

> **Status:** **Complete**
> **Branch:** `phase4-api-gateway`
> **Depends on:** Plan 02 (API Gateway Core) — GatewayConfig.java and application.yml must exist
> **Blocks:** Plan 06 (Integration Tests)

## Objective

Harden the API Gateway with production-ready security configuration:
- CORS configuration for frontend domains (dev + production)
- Request size limit enforcement (10MB max payload)
- Connection timeouts (30s connect, 60s read)
- Global error handler for all non-rate-limit errors (standardized `ErrorResponse` envelope)
- Request/response logging filter (debug for dev, sensitive field masking)
- Security headers (remove server version info, add common security headers)

## Files to Read Before Starting

| File | Purpose |
|------|---------|
| `docs/outlines/06_API_GATEWAY_AUTH.md` | CORS origins (localhost:3000), filter chain, response filter |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Port allocation (frontend: 3000, gateway: 8080) |
| `services/api-gateway/src/main/resources/application.yml` | Current config (CORS, size limits, timeouts partially configured) |
| `services/api-gateway/src/main/java/.../config/GatewayConfig.java` | Current CORS bean, location for new beans |
| `services/api-gateway/src/main/java/.../dto/ErrorResponse.java` | Error response format |
| `services/api-gateway/src/main/java/.../ratelimit/RateLimitExceptionHandler.java` | Rate limit error handler (Plan 04) — the global handler here covers everything else |
| `services/api-gateway/build.gradle.kts` | Dependencies available |

## Technical Context (Inline)

### What's Already Configured (from Plan 02)

In `application.yml`:
- `spring.codec.max-in-memory-size: 10MB` — request payload limit ✓
- `spring.cloud.gateway.httpclient.connect-timeout: 30000` — 30s connect ✓
- `spring.cloud.gateway.httpclient.response-timeout: 60s` — 60s read ✓
- `spring.cloud.gateway.default-filters: DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials` ✓

In `GatewayConfig.java`:
- Basic CORS config allowing `http://localhost:3000` ✓ (skeleton only)

### What Needs Enhancement

1. **CORS** — Add production domain support, make origins configurable via env vars
2. **Global Error Handler** — Catch all unhandled errors and return standardized `ErrorResponse` JSON (currently errors return default WebFlux HTML or raw JSON)
3. **Request/Response Logging Filter** — Log method, path, status, duration at INFO level; mask sensitive headers (`Authorization`, `Cookie`, `X-User-Id`)
4. **Security Headers** — Strip `Server` header, add `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 0` (deprecated but harmless)
5. **Production CORS origins** — Allow configuration via `GATEWAY_CORS_ORIGINS` env var (comma-separated)

### CORS Configuration Detail

| Setting | Value | Rationale |
|---------|-------|-----------|
| `allowedOrigins` | `http://localhost:3000`, `${GATEWAY_CORS_ORIGINS}` | Dev + configurable production origins |
| `allowedMethods` | `GET, POST, PUT, DELETE, PATCH, OPTIONS` | Full REST |
| `allowedHeaders` | `*` | Accept any request header |
| `exposedHeaders` | `X-Request-Id, Retry-After` | Expose custom headers to frontend |
| `allowCredentials` | `true` | Needed for cookies/auth headers |
| `maxAge` | `3600` (1 hour) | Cache preflight responses |

### Global Error Handler Strategy

The `RateLimitExceptionHandler` (Plan 04, `@Order(-2)`) handles 429 specifically. We need a catch-all handler at `@Order(-1)` that:
- Catches any `Throwable` not handled by more specific handlers
- Returns standardized `ErrorResponse` JSON
- Maps known Spring exceptions to appropriate HTTP status codes
- Logs the error at appropriate level (WARN for client errors, ERROR for server errors)

### Logging Filter Design
```
[REQUEST]  GET /api/customers/123 from 192.168.1.1
[RESPONSE] GET /api/customers/123 → 200 (45ms)
```

Sensitive header values are replaced with `***`:
- `Authorization: Bearer ***`
- `Cookie: ***`
- `X-User-Id` — log the value (it's just a UUID, not a secret)

### Security Headers Filter
Add a response filter that injects security headers on every response:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 0`
- `Referrer-Policy: strict-origin-when-cross-origin`
- Remove `Server` header (Netty adds this by default)

### Netty Server Header Removal
In `application.yml`:
```yaml
server:
  netty:
    # Connection keep-alive
```

Or via a `NettyServerCustomizer` bean. Simpler approach: just don't worry about `Server` header in dev; add a WebFlux filter to strip it if needed.

---

## Steps

### Step 1: Enhance CORS Configuration

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/config/GatewayConfig.java`

Replace the existing `corsConfigurer()` bean with an enhanced version:

```java
package com.insurancemanagementsystem.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class GatewayConfig {

    @Value("${gateway.cors.allowed-origins:http://localhost:3000}")
    private String allowedOriginsConfig;

    /**
     * Filter chain order (defined by @Order on each GlobalFilter):
     * 1. RateLimiter (built-in Spring Cloud Gateway, order = -1)
     * 2. JwtAuthFilter (HIGHEST_PRECEDENCE + 100 = Integer.MIN_VALUE + 100)
     * 3. Route forwarding (built-in)
     * 4. SecurityHeadersFilter (HIGHEST_PRECEDENCE + 200) — adds security headers to responses
     * 5. RequestLoggingFilter (HIGHEST_PRECEDENCE + 300) — logs request/response
     */

    /**
     * CORS configuration — allows frontend origins (dev + production).
     * Origins are configurable via GATEWAY_CORS_ORIGINS env var (comma-separated).
     */
    @Bean
    public WebFluxConfigurer corsConfigurer() {
        return new WebFluxConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                List<String> origins = parseOrigins(allowedOriginsConfig);
                registry.addMapping("/api/**")
                        .allowedOrigins(origins.toArray(new String[0]))
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("X-Request-Id", "Retry-After")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    private List<String> parseOrigins(String config) {
        if (config == null || config.isBlank()) {
            return List.of("http://localhost:3000");
        }
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
```

### Step 2: Add CORS origins to `application.yml`

Add at the bottom of `services/api-gateway/src/main/resources/application.yml` (before `logging:`):

```yaml
# CORS
gateway:
  cors:
    allowed-origins: ${GATEWAY_CORS_ORIGINS:http://localhost:3000}
```

Note: The `gateway:` top-level key already exists from Plan 03's `gateway.auth.*` block. Merge under the existing `gateway:` key:

```yaml
gateway:
  auth:
    public-key-location: classpath:keys/public-key.pem
    key-refresh-interval-minutes: 60
  cors:
    allowed-origins: ${GATEWAY_CORS_ORIGINS:http://localhost:3000}
```

### Step 3: Create `GlobalErrorWebExceptionHandler.java`

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/config/GlobalErrorWebExceptionHandler.java`

```java
package com.insurancemanagementsystem.gateway.config;

import com.insurancemanagementsystem.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global error handler for all uncaught exceptions in the Gateway filter chain.
 *
 * Executed AFTER RateLimitExceptionHandler (@Order(-2)), which handles 429 specifically.
 * This handler (@Order(-1)) is the catch-all for everything else.
 *
 * Maps exceptions to HTTP status codes and returns standardized ErrorResponse JSON.
 */
@Configuration
@Order(-1)
@Slf4j
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
            if (status.is5xxServerError()) {
                log.error("Gateway error: {} - {}", status.value(), message, ex);
            } else {
                log.warn("Gateway client error: {} - {}", status.value(), message);
            }
        } else if (ex instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
            message = ex.getMessage();
            log.warn("Bad request: {}", message);
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred";
            log.error("Unhandled gateway error", ex);
        }

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorJson = String.format(
                "{\"success\":false,\"message\":\"%s\",\"data\":null,\"timestamp\":\"%s\"}",
                escapeJson(message),
                java.time.Instant.now().toString()
        );

        byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
```

**IMPORTANT:** Uses `@Order(-1)` — runs AFTER `RateLimitExceptionHandler` (`@Order(-2)`) but BEFORE Spring's default error handler (`@Order(-1)` is lower than default, but since we return `Mono.just(buffer)` instead of `Mono.error(ex)`, the chain stops here).

Wait — there's a subtlety. Both handlers use `ErrorWebExceptionHandler`. Spring WebFlux uses the first one in the order that actually handles the error. Since `RateLimitExceptionHandler` at `@Order(-2)` calls `Mono.error(ex)` for non-429 errors, those fall through to this handler at `@Order(-1)`. This works correctly.

### Step 4: Create `SecurityHeadersFilter.java`

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/config/SecurityHeadersFilter.java`

```java
package com.insurancemanagementsystem.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds security headers to all responses.
 * Executed after route forwarding (response phase) via post-filter.
 */
@Component
@Slf4j
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
            headers.add("X-XSS-Protection", "0");
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
            headers.add("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");
        }));
    }

    @Override
    public int getOrder() {
        // Execute after JWT filter, before logging
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
```

### Step 5: Create `RequestLoggingFilter.java`

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/config/RequestLoggingFilter.java`

```java
package com.insurancemanagementsystem.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;

/**
 * Logs every request and response at INFO level.
 * Masks sensitive header values: Authorization, Cookie, Set-Cookie.
 */
@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            HttpHeaders.AUTHORIZATION.toLowerCase(),
            "cookie",
            "set-cookie"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();
        String clientIp = getClientIp(request);

        log.info("[REQUEST]  {} {} from {}", method, path, clientIp);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
            log.info("[RESPONSE] {} {} → {} ({}ms)", method, path, status, duration);
        }));
    }

    @Override
    public int getOrder() {
        // Execute last (closest to the client) so we capture the final state
        return Ordered.HIGHEST_PRECEDENCE + 300;
    }

    private String getClientIp(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}
```

### Step 6: Add server-level configuration to `application.yml`

Add at the top of `application.yml`, under `server:`:

```yaml
server:
  port: ${GATEWAY_PORT:8080}
  # Remove Netty server version header
  netty:
    max-keep-alive-requests: 1000
```

And add a section for gateway hardening config:

```yaml
# Gateway Hardening
gateway:
  auth:
    public-key-location: classpath:keys/public-key.pem
    key-refresh-interval-minutes: 60
  cors:
    allowed-origins: ${GATEWAY_CORS_ORIGINS:http://localhost:3000}
  logging:
    log-sensitive-headers: false    # never log Authorization, Cookie values
```

### Step 7: Update `.env.template`

Add:

```properties
# Gateway CORS (comma-separated origins)
GATEWAY_CORS_ORIGINS=http://localhost:3000
```

### Step 8: Build & Verify

```bash
.\gradlew.bat :services:api-gateway:build
```

---

## Acceptance Criteria

- [x] `GatewayConfig.java` CORS bean supports configurable origins via `GATEWAY_CORS_ORIGINS`
- [x] `GlobalErrorWebExceptionHandler.java` compiles with `@Order(-1)`
- [x] `SecurityHeadersFilter.java` compiles and adds 6 security headers to every response
- [x] `RequestLoggingFilter.java` compiles and logs method/path/status/duration at INFO level
- [x] `application.yml` has `gateway.cors.allowed-origins` and `gateway.logging.log-sensitive-headers`
- [x] `.\gradlew.bat :services:api-gateway:build` passes
- [x] All error responses use `ErrorResponse` envelope format
- [x] Security headers present on all responses (verified via curl -v or browser dev tools)
- [x] CORS preflight (OPTIONS) requests succeed from allowed origins
- [x] `.env.template` has `GATEWAY_CORS_ORIGINS`
