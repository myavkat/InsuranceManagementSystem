# Task: Phase 4 — API Gateway & Service Discovery

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/06_API_GATEWAY_AUTH.md

## Objective
Set up the API Gateway (Spring Cloud Gateway) as the single entry point for all external requests. Configure service discovery, routing rules, JWT validation, rate limiting, CORS, and security.

### Subtasks

1. **Setup API Gateway Service**
   - Create `services/api-gateway/` with Spring Cloud Gateway.
   - Dependencies: `spring-cloud-starter-gateway`, `spring-cloud-starter-netflix-eureka-client` (or Consul), `spring-cloud-starter-circuitbreaker-reactor-resilience4j`.

2. **Configure Service Discovery**
   - Deploy Eureka server (or Consul) as a standalone service or embedded in Gateway.
   - All microservices register with Eureka on startup.
   - Gateway uses `lb://` scheme for load-balanced routing to registered services.

3. **Define API Routing Rules**
   ```
   /api/auth/**          → lb://auth-service
   /api/customers/**     → lb://customer-service
   /api/vehicles/**      → lb://vehicle-service
   /api/real-estate/**   → lb://realestate-service
   /api/insurances/**    → lb://insurance-service
   /api/estimations/**   → lb://estimation-service
   /api/reference-data/** → lb://reference-data-service
   ```
   - Strip prefix: Gateway strips `/api` before forwarding.
   - Timeout per route: default 5s, configurable.

4. **Implement JWT Verification Filter**
   - Global filter that intercepts all requests except `/api/auth/login`, `/api/auth/register`, `/api/auth/refresh`.
   - Extracts JWT from `Authorization: Bearer <token>` header.
   - Validates JWT locally using Auth Service's public RSA key (cached, refreshed periodically).
   - On validation success: injects `X-User-Id`, `X-User-Roles` headers to downstream services.
   - On failure: returns 401 with standardized error response.

5. **Configure Rate Limiting**
   - Redis-backed token bucket rate limiter.
   - Default: 100 requests/minute per user (identified by `X-User-Id`), 1000/minute per IP.
   - Route-specific overrides: `/api/auth/login` → 10/minute per IP.
   - Rate limit exceeded: returns 429 with `Retry-After` header.

6. **Configure Security & Limits**
   - CORS configuration: allow origins from frontend domains (localhost:3000, production domains).
   - Request size limit: 10MB max payload.
   - Connection timeout: 30s connect, 60s read.
   - Global error handler: standardizes all error responses to `ApiResponse` envelope.
   - Request/response logging filter (debug level for dev, info for production, never log sensitive fields).

### Deliverables
- Spring Cloud Gateway service operational
- All microservices registered with service discovery
- Routing rules configured and tested for all service paths
- JWT verification filter with local public key caching
- Redis-backed rate limiting with per-route overrides
- CORS, size limits, timeouts configured
- Gateway integration test suite (routing, auth, rate limiting)
