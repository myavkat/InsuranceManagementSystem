# Plan 04: Rate Limiting with Redis

> **Status:** Not started
> **Branch:** `phase4-api-gateway`
> **Depends on:** Plan 02 (API Gateway Core) — GatewayConfig.java and application.yml must exist
> **Blocks:** Plan 06 (Integration Tests)

## Objective

Configure Redis-backed token bucket rate limiting for the API Gateway. Implement per-route rate limits with the following defaults and overrides:
- Default: 100 requests/minute per authenticated user (keyed by `X-User-Id` header)
- Default fallback: 1000 requests/minute per IP address (when `X-User-Id` is not available)
- `/api/auth/login`: 10 requests/minute per IP (login brute-force protection)
- Rate limit exceeded → `429 Too Many Requests` with `Retry-After` header and standardized `ErrorResponse` body

## Files to Read Before Starting

| File | Purpose |
|------|---------|
| `docs/outlines/06_API_GATEWAY_AUTH.md` | Rate limiting spec (100/min user, 1000/min IP, 10/min login) |
| `services/api-gateway/src/main/resources/application.yml` | Current routes, Redis config already present |
| `services/api-gateway/src/main/java/.../config/GatewayConfig.java` | Where rate limiter beans are registered |
| `services/api-gateway/src/main/java/.../dto/ErrorResponse.java` | Error response format |
| `services/api-gateway/build.gradle.kts` | `spring-boot-starter-data-redis-reactive` already included |
| `infra/docker/docker-compose.yml` | Redis container already defined (line 275-292) — running on port 6379 |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Redis host/port env vars (`REDIS_HOST`, `REDIS_PORT`) |

## Technical Context (Inline)

### Spring Cloud Gateway Rate Limiter
Spring Cloud Gateway provides a built-in `RequestRateLimiter` gateway filter factory. It requires:
1. A `KeyResolver` bean — determines what to key the rate limit on (user ID, IP, etc.)
2. A `RateLimiter` implementation — token bucket algorithm, backed by Redis

The built-in Redis implementation uses `spring-boot-starter-data-redis-reactive`.

### Rate Limit Configuration Model

| Route | Limit | Key Source | Notes |
|-------|-------|------------|-------|
| `/api/auth/**` (login specifically) | 10 req/min | IP address | Brute-force protection |
| All other authenticated routes | 100 req/min | `X-User-Id` header (fallback: IP) | Per-user rate limit |
| Public routes (reference-data) | 1000 req/min | IP address | Higher limit for non-auth |

### Token Bucket Parameters

Spring Cloud Gateway's `RequestRateLimiter` uses these properties:
- `redis-rate-limiter.replenishRate` — tokens per second (e.g., 100/min ≈ 1.66 tokens/sec)
- `redis-rate-limiter.burstCapacity` — max burst (same as replenish rate for steady flow)
- `redis-rate-limiter.requestedTokens` — tokens consumed per request (default: 1)

**Conversion:**
- 100 req/min → `replenishRate=1.66`, `burstCapacity=100`
- 1000 req/min → `replenishRate=16.67`, `burstCapacity=1000`
- 10 req/min → `replenishRate=0.167`, `burstCapacity=10`

Since `replenishRate` expects an integer in some implementations, we'll use a custom approach: configure the Redis rate limiter with `replenishRate=1` and set `burstCapacity` to the per-minute limit. The token refill happens every second.

### KeyResolver Strategy

Need a **composite KeyResolver** that:
1. Checks for `X-User-Id` header (set by JwtAuthFilter)
2. Falls back to the client IP address (`X-Forwarded-For` or `RemoteAddr`)

For the login route, always use IP-based keying (even if a user is somehow authenticated).

### Redis Availability
The Redis container is already defined in `infra/docker/docker-compose.yml` (line 275-292):
```yaml
redis:
  image: redis:7-alpine
  container_name: redis
  ports:
    - "6379:6379"
  networks:
    - insurance-net
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
```

The Gateway's `application.yml` already has Redis connection config (added in Plan 02):
```yaml
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
```

### Filter Order
Rate limiter must execute BEFORE JWT filter. Spring Cloud Gateway's built-in `RequestRateLimiter` uses order `-1` (higher precedence than `Ordered.HIGHEST_PRECEDENCE`). The JWT filter uses `HIGHEST_PRECEDENCE + 100`. So the order is correct: rate limiter → JWT → route.

### 429 Error Response Format
```json
{
  "success": false,
  "message": "Rate limit exceeded. Try again in 45 seconds.",
  "data": null,
  "timestamp": "2026-07-08T12:00:00Z"
}
```
Plus `Retry-After: 45` HTTP header.

---

## Steps

### Step 1: Create `RateLimitKeyResolver.java`

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/ratelimit/RateLimitKeyResolver.java`

```java
package com.insurancemanagementsystem.gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Composite KeyResolver for rate limiting.
 *
 * Priority:
 * 1. X-User-Id header (authenticated user) — set by JwtAuthFilter
 * 2. Client IP address (unauthenticated or fallback)
 *
 * The key format distinguishes the source:
 * - "user:<uuid>" for authenticated requests
 * - "ip:<address>" for unauthenticated requests
 */
@Component
@Slf4j
public class RateLimitKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(org.springframework.web.server.ServerWebExchange exchange) {
        // 1. Try X-User-Id (authenticated user)
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return Mono.just("user:" + userId);
        }

        // 2. Fall back to client IP
        String ip = getClientIp(exchange);
        return Mono.just("ip:" + ip);
    }

    /**
     * Resolves client IP from X-Forwarded-For or remote address.
     */
    private String getClientIp(org.springframework.web.server.ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take the first IP in the chain (original client)
            return forwardedFor.split(",")[0].trim();
        }
        // Fall back to direct remote address
        return Objects.requireNonNullElse(
                exchange.getRequest().getRemoteAddress(),
                new java.net.InetSocketAddress("unknown", 0)
        ).getAddress().getHostAddress();
    }
}
```

### Step 2: Create `LoginIpKeyResolver.java`

A dedicated KeyResolver for the login endpoint that ALWAYS keys by IP (never by user — the user isn't authenticated yet).

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/ratelimit/LoginIpKeyResolver.java`

```java
package com.insurancemanagementsystem.gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * IP-only KeyResolver for the login endpoint.
 * Always keys by client IP — never by X-User-Id (user is not yet authenticated).
 */
@Component("loginIpKeyResolver")
@Slf4j
public class LoginIpKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(org.springframework.web.server.ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        String ip;
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            ip = forwardedFor.split(",")[0].trim();
        } else {
            ip = Objects.requireNonNullElse(
                    exchange.getRequest().getRemoteAddress(),
                    new java.net.InetSocketAddress("unknown", 0)
            ).getAddress().getHostAddress();
        }
        return Mono.just("login-ip:" + ip);
    }
}
```

### Step 3: Configure Rate Limiter in `application.yml`

Edit `services/api-gateway/src/main/resources/application.yml`.

**3a. Add Redis rate limiter defaults** (under `spring.cloud.gateway`):

Add this under `spring.cloud.gateway:`:

```yaml
      # Redis Rate Limiter defaults
      redis-rate-limiter:
        include-headers: true   # adds X-RateLimit-Remaining, X-RateLimit-Burst-Limit, X-RateLimit-Retry-After-Seconds
```

Place inside the existing `spring.cloud.gateway:` block, at the same level as `routes:` and `default-filters:`.

**3b. Add rate limit filter to EACH route definition**

For the **auth-service route**, add the rate limiter filter with the IP key resolver and login-specific limits:

```yaml
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 10
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@loginIpKeyResolver}"
          metadata:
            auth-required: false
```

For all **other routes** (customer, vehicle, realestate, insurance, estimation, reference-data), add the rate limiter filter with the composite key resolver and default limits (1000/min for public, 100/min for authenticated — using the same burst since the resolver already distinguishes):

```yaml
        - id: customer-service
          uri: lb://customer-service
          predicates:
            - Path=/api/customers/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 100
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@rateLimitKeyResolver}"
          metadata:
            auth-required: true
```

**For reference-data (public, high volume):**

```yaml
        - id: reference-data-service
          uri: lb://reference-data-service
          predicates:
            - Path=/api/reference-data/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 1000
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@rateLimitKeyResolver}"
          metadata:
            auth-required: false
```

**IMPORTANT Bean reference syntax:** `#{@loginIpKeyResolver}` and `#{@rateLimitKeyResolver}` use Spring EL to reference beans by their Spring bean names. The `@Component` annotation on `RateLimitKeyResolver` gives it bean name `rateLimitKeyResolver`. The `@Component("loginIpKeyResolver")` on `LoginIpKeyResolver` gives it that explicit name.

### Step 4: Create `RateLimitExceptionHandler.java`

Overrides the default 429 response to return our standardized `ErrorResponse` format with `Retry-After` header.

**File:** `services/api-gateway/src/main/java/com/insurancemanagementsystem/gateway/ratelimit/RateLimitExceptionHandler.java`

```java
package com.insurancemanagementsystem.gateway.ratelimit;

import com.insurancemanagementsystem.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
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
 * Overrides Gateway's default 429 response to return standardized ErrorResponse JSON
 * with Retry-After header.
 *
 * This handler catches {@link ResponseStatusException} with status 429 (TOO_MANY_REQUESTS)
 * thrown by the RequestRateLimiter filter.
 */
@Configuration
@Order(-2) // Before default error handlers
@Slf4j
public class RateLimitExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (ex instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            return handleRateLimit(response);
        }

        // Fall through to next error handler for non-rate-limit errors
        return Mono.error(ex);
    }

    private Mono<Void> handleRateLimit(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Extract retry-after from response headers set by the rate limiter
        String retryAfter = response.getHeaders().getFirst("X-RateLimit-Retry-After-Seconds");
        if (retryAfter != null) {
            response.getHeaders().set("Retry-After", retryAfter);
        } else {
            response.getHeaders().set("Retry-After", "60");
        }

        String errorJson = String.format(
                "{\"success\":false,\"message\":\"Rate limit exceeded. Try again in %s seconds.\",\"data\":null,\"timestamp\":\"%s\"}",
                retryAfter != null ? retryAfter : "60",
                java.time.Instant.now().toString()
        );

        byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
```

### Step 5: Build & Verify

Ensure Redis is running:
```bash
docker compose -f infra/docker/docker-compose.yml up -d redis
```

```bash
.\gradlew.bat :services:api-gateway:build
```

Fix any compilation errors before proceeding.

### Step 6: Manual verification (optional but recommended)

Start Eureka (Plan 01), then Gateway:
```bash
.\gradlew.bat :services:api-gateway:bootRun
```

Send rapid requests to verify 429 response:
```bash
# Using curl in a loop (Git Bash):
for i in $(seq 1 15); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/reference-data/cities
done
```

After the burst capacity is exhausted, expect `429` responses with `Retry-After` header.

---

## Acceptance Criteria

- [ ] `RateLimitKeyResolver.java` compiles and implements `KeyResolver`
- [ ] `LoginIpKeyResolver.java` compiles with bean name `loginIpKeyResolver`
- [ ] `RateLimitExceptionHandler.java` compiles and implements `ErrorWebExceptionHandler`
- [ ] All 7 routes in `application.yml` have `RequestRateLimiter` filter configured
- [ ] Auth service route uses `loginIpKeyResolver` with burst=10 (10 req/min)
- [ ] Authenticated routes use `rateLimitKeyResolver` with burst=100 (100 req/min)
- [ ] Reference data route uses `rateLimitKeyResolver` with burst=1000 (1000 req/min)
- [ ] `spring.cloud.gateway.redis-rate-limiter.include-headers: true` is set
- [ ] 429 response includes `Retry-After` header and standardized `ErrorResponse` JSON
- [ ] `.\gradlew.bat :services:api-gateway:build` passes
- [ ] Rapid requests to any route eventually return 429
