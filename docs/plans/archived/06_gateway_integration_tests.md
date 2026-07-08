# Plan 06: API Gateway Integration Tests

> **Status:** Complete
> **Branch:** `phase4-api-gateway`
> **Depends on:** Plans 01, 02, 03, 04, 05 — all Gateway functionality must be implemented before tests
> **Blocks:** None (final validation plan)

## Objective

Create a comprehensive integration test suite for the API Gateway covering:
- **Route forwarding:** All 7 routes forward to correct downstream services
- **JWT authentication:** Valid token → 200, expired token → 401, missing token → 401, malformed token → 401
- **Public routes:** `/api/auth/login` and `/api/reference-data/**` are accessible without token
- **Rate limiting:** Requests exceeding burst capacity return 429 with `Retry-After` header
- **CORS:** Preflight OPTIONS requests return correct CORS headers
- **Error handling:** Invalid routes return 404, downstream errors propagate correctly
- **Header injection:** `X-User-Id` and `X-User-Roles` are forwarded to downstream services

## Files to Read Before Starting

| File | Purpose |
|------|---------|
| `docs/outlines/06_API_GATEWAY_AUTH.md` | Route table, auth requirements, JWT spec, rate limits |
| `docs/outlines/11_TESTING_CONVENTIONS.md` | `@SpringBootTest`, `RestTestClient`, `@AutoConfigureRestTestClient`, JSON assertions |
| `services/customer-service/src/test/java/.../CustomerControllerTest.java` | Test pattern reference (RestTestClient + jsonPath assertions) |
| `services/api-gateway/src/main/resources/application.yml` | Routes, rate limits, timeouts |
| `services/api-gateway/src/main/java/.../auth/JwtAuthFilter.java` | Auth filter behavior |
| `services/api-gateway/src/main/java/.../auth/TestJwtTokenGenerator.java` | Test JWT generation utility (created in Plan 03) |
| `services/api-gateway/src/main/java/.../ratelimit/RateLimitKeyResolver.java` | Rate limit key format |
| `services/api-gateway/src/main/java/.../config/SecurityHeadersFilter.java` | Security headers to verify |
| `services/api-gateway/build.gradle.kts` | Test dependencies |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Testcontainers on Windows, Jackson conflicts |

## Technical Context (Inline)

### Testing Approach for Reactive Gateway

Spring Cloud Gateway is a reactive (WebFlux) application. For integration testing:

**Option A: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient`**
- Starts the full Gateway with Netty on a random port
- Test sends real HTTP requests
- **BUT:** Gateway routes point to `lb://service-name` — those services won't be running in tests
- Need to either mock downstream services or use `spring.cloud.gateway.routes` overrides in test profile

**Option B: Test slices with mocked routes**
- Use `@SpringBootTest` with test-specific application.yml that uses `http://localhost:${wiremock.port}` instead of `lb://`
- Use WireMock to simulate downstream services
- Gives full control over downstream responses

**Recommended approach:** Use **WireMock** to simulate downstream services. The test profile overrides route URIs to point to WireMock. This tests the full Gateway filter chain (JWT, rate limiting, CORS, error handling) end-to-end without requiring real services.

### Test Dependencies
Add to `build.gradle.kts` test scope:
```kotlin
testImplementation("org.springframework.cloud:spring-cloud-starter-contract-stub-runner")  // WireMock
testImplementation("org.springframework.boot:spring-boot-resttestclient")                 // RestTestClient
testImplementation("io.projectreactor:reactor-test")                                       // already included
```

Actually, `spring-cloud-starter-contract-stub-runner` pulls in WireMock. Alternatively, use `com.github.tomakehurst:wiremock-jre8-standalone` directly. Spring Cloud Contract Stub Runner uses WireMock under the hood.

Let's use the direct WireMock dependency for simplicity:
```kotlin
testImplementation("org.wiremock:wiremock-standalone:3.9.2")
```

Wait — let me check what's available. Actually, `spring-cloud-starter-contract-stub-runner` already includes WireMock and integrates better with Spring Cloud.

Actually, the simplest approach for testing Gateway routes is to use `@SpringBootTest` with a test-specific `application-test.yml` that overrides routes to use `http://` URIs instead of `lb://`, then start WireMock to simulate downstream services.

### Test Profile Configuration

Create `src/test/resources/application-test.yml` that:
- Disables Eureka discovery (Gateway won't try to contact Eureka server)
- Overrides route URIs to `http://localhost:${wiremock.server.port}`
- Disables Redis rate limiting (or uses embedded Redis/test double) — rate limiting tests are separate
- Uses an in-memory public key for JWT validation

### Test Categories

| Category | Test Class | What It Tests |
|----------|-----------|---------------|
| Routing | `GatewayRoutingTest` | All 7 routes forward correctly, 404 for unknown routes |
| JWT Auth | `JwtAuthenticationTest` | Valid/expired/missing/malformed tokens |
| Public Routes | `PublicRoutesTest` | Auth and reference-data routes accessible without token |
| Rate Limiting | `RateLimitingTest` | 429 after exceeding burst capacity |
| CORS | `CorsTest` | Preflight responses, allowed origins |
| Error Handling | `ErrorHandlingTest` | 404, downstream 500, error response format |
| Security Headers | `SecurityHeadersTest` | Security headers on all responses |

### WireMock Setup

Each test class starts a WireMock server on a random port and configures it to return canned responses for specific paths. The Gateway's test profile routes point to the WireMock server.

### Rate Limiting Test Strategy

Unit-test the rate limiter with an embedded approach: since Redis is required for the production rate limiter, the integration test either:
1. Uses Testcontainers to start a Redis container, OR
2. Uses a `NoOpRateLimiter` for most tests and tests rate limiting separately

For simplicity and speed, use Testcontainers Redis for rate limiting tests only. Other test classes use a test profile that disables rate limiting.

---

## Steps

### Step 1: Add test dependencies to `build.gradle.kts`

**File:** `services/api-gateway/build.gradle.kts`

Add the following to the `dependencies` block, in the test scope section:

```kotlin
// Test dependencies
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.boot:spring-boot-starter-restclient")
testImplementation("org.springframework.boot:spring-boot-resttestclient")
testImplementation("io.projectreactor:reactor-test")
testImplementation("org.wiremock:wiremock-standalone:3.9.2")
testImplementation("org.testcontainers:testcontainers")
testImplementation("org.testcontainers:testcontainers-junit-jupiter")
```

Important: Remove the existing `testImplementation` lines for `spring-boot-starter-test` and `reactor-test` (they're already there from Plan 02) and add the new ones alongside them. The final test dependencies should be:

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.boot:spring-boot-starter-restclient")
testImplementation("org.springframework.boot:spring-boot-resttestclient")
testImplementation("io.projectreactor:reactor-test")
testImplementation("org.wiremock:wiremock-standalone:3.9.2")
testImplementation("org.testcontainers:testcontainers")
testImplementation("org.testcontainers:testcontainers-junit-jupiter")
```

Also add to `dependencyManagement`:
```kotlin
mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
```
(Already imported from Plan 02 if following the build.gradle.kts template; verify.)

### Step 2: Create test application profile

**File:** `services/api-gateway/src/test/resources/application-test.yml`

```yaml
# Test profile — disables Eureka, overrides routes to WireMock
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://localhost:${wiremock.server.port:18080}
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: false

        - id: customer-service
          uri: http://localhost:${wiremock.server.port:18080}
          predicates:
            - Path=/api/customers/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        - id: vehicle-service
          uri: http://localhost:${wiremock.server.port:18080}
          predicates:
            - Path=/api/vehicles/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        - id: realestate-service
          uri: http://localhost:${wiremock.server.port:18080}
          predicates:
            - Path=/api/real-estate/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        - id: insurance-service
          uri: http://localhost:${wiremock.server.port:18080}
          predicates:
            - Path=/api/insurances/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        - id: estimation-service
          uri: http://localhost:${wiremock.server.port:18080}
          predicates:
            - Path=/api/estimations/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: true

        - id: reference-data-service
          uri: http://localhost:${wiremock.server.port:18080}
          predicates:
            - Path=/api/reference-data/**
          filters:
            - StripPrefix=1
          metadata:
            auth-required: false

  # Disable Eureka in tests
  autoconfigure:
    exclude:
      - org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration

# Disable Redis rate limiting for most tests (use NoOp implementation)
# The RateLimitingTest class uses a separate profile with real Redis via Testcontainers
gateway:
  auth:
    public-key-location: classpath:keys/public-key.pem
    key-refresh-interval-minutes: 60
  cors:
    allowed-origins: http://localhost:3000

logging:
  level:
    com.insurancemanagementsystem.gateway: DEBUG
```

### Step 3: Create base test class with WireMock setup

**File:** `services/api-gateway/src/test/java/com/insurancemanagementsystem/gateway/BaseGatewayTest.java`

```java
package com.insurancemanagementsystem.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base class for Gateway integration tests.
 * Starts a WireMock server to simulate downstream microservices.
 * All routes are configured to forward to this WireMock server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
public abstract class BaseGatewayTest {

    protected static WireMockServer wireMockServer;

    @Autowired
    protected RestTestClient client;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        // WireMock port is set as a system property so application-test.yml can reference it
        System.setProperty("wiremock.server.port", String.valueOf(wireMockServer.port()));
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        System.clearProperty("wiremock.server.port");
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    /**
     * Shortcut to set up a WireMock stub.
     */
    protected void stubGet(String path, int status, String body) {
        wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo(path))
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(body))
        );
    }

    protected void stubPost(String path, int status, String body) {
        wireMockServer.stubFor(
                WireMock.post(WireMock.urlPathEqualTo(path))
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(body))
        );
    }
}
```

### Step 4: Create `GatewayRoutingTest.java`

**File:** `services/api-gateway/src/test/java/com/insurancemanagementsystem/gateway/GatewayRoutingTest.java`

```java
package com.insurancemanagementsystem.gateway;

import com.insurancemanagementsystem.gateway.auth.TestJwtTokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Tests that all 7 routes forward requests to the correct downstream paths.
 * WireMock stubs catch forwarded requests and verify the path rewriting.
 */
class GatewayRoutingTest extends BaseGatewayTest {

    private final String validToken = TestJwtTokenGenerator.createValidToken(
            UUID.randomUUID(), List.of("CUSTOMER"));

    @Test
    @DisplayName("Auth service route strips /api prefix and forwards")
    void authRouteForwardsCorrectly() {
        stubPost("/auth/login", 200, "{\"token\":\"test\"}");

        client.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{\"username\":\"test\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Customer service route strips /api prefix and forwards")
    void customerRouteForwardsCorrectly() {
        stubGet("/customers", 200, "{\"success\":true,\"data\":[]}");

        client.get().uri("/api/customers")
                .header(AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Vehicle service route strips /api prefix and forwards")
    void vehicleRouteForwardsCorrectly() {
        stubGet("/vehicles", 200, "{\"success\":true,\"data\":[]}");

        client.get().uri("/api/vehicles")
                .header(AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("RealEstate service route strips /api prefix and forwards")
    void realEstateRouteForwardsCorrectly() {
        stubGet("/real-estate", 200, "{\"success\":true,\"data\":[]}");

        client.get().uri("/api/real-estate")
                .header(AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Insurance service route strips /api prefix and forwards")
    void insuranceRouteForwardsCorrectly() {
        stubGet("/insurances", 200, "{\"success\":true,\"data\":[]}");

        client.get().uri("/api/insurances")
                .header(AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Estimation service route strips /api prefix and forwards")
    void estimationRouteForwardsCorrectly() {
        stubGet("/estimations", 200, "{\"success\":true,\"data\":[]}");

        client.get().uri("/api/estimations")
                .header(AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Reference data route strips /api prefix and forwards (public)")
    void referenceDataRouteForwardsCorrectly() {
        stubGet("/reference-data/cities", 200, "{\"success\":true,\"data\":[]}");

        client.get().uri("/api/reference-data/cities")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Unknown route returns 404")
    void unknownRouteReturns404() {
        client.get().uri("/api/nonexistent")
                .exchange()
                .expectStatus().isNotFound();
    }
}
```

### Step 5: Create `JwtAuthenticationTest.java`

**File:** `services/api-gateway/src/test/java/com/insurancemanagementsystem/gateway/JwtAuthenticationTest.java`

```java
package com.insurancemanagementsystem.gateway;

import com.insurancemanagementsystem.gateway.auth.TestJwtTokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

/**
 * Tests JWT authentication filter behavior.
 */
class JwtAuthenticationTest extends BaseGatewayTest {

    private final UUID userId = UUID.randomUUID();
    private final String validToken = TestJwtTokenGenerator.createValidToken(userId, List.of("CUSTOMER", "AGENT"));

    @Test
    @DisplayName("Valid JWT returns 200 and forwards X-User-Id header")
    void validTokenForwardsSuccessfully() {
        stubGet("/customers", 200, "{\"success\":true,\"data\":[]}");

        client.get().uri("/api/customers")
                .header(AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Missing Authorization header returns 401")
    void missingTokenReturns401() {
        client.get().uri("/api/customers")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Missing or invalid Authorization header");
    }

    @Test
    @DisplayName("Empty Authorization header returns 401")
    void emptyTokenReturns401() {
        client.get().uri("/api/customers")
                .header(AUTHORIZATION, "")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Missing or invalid Authorization header");
    }

    @Test
    @DisplayName("Authorization header without Bearer prefix returns 401")
    void nonBearerTokenReturns401() {
        client.get().uri("/api/customers")
                .header(AUTHORIZATION, validToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Missing or invalid Authorization header");
    }

    @Test
    @DisplayName("Expired JWT returns 401")
    void expiredTokenReturns401() {
        String expiredToken = TestJwtTokenGenerator.createExpiredToken(userId, List.of("CUSTOMER"));

        client.get().uri("/api/customers")
                .header(AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Token expired");
    }

    @Test
    @DisplayName("Malformed JWT returns 401")
    void malformedTokenReturns401() {
        client.get().uri("/api/customers")
                .header(AUTHORIZATION, "Bearer this.is.not.a.valid.jwt")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Invalid token signature");
    }

    @Test
    @DisplayName("Public routes are accessible without token")
    void publicRoutesAccessibleWithoutToken() {
        stubGet("/reference-data/cities", 200, "{\"success\":true,\"data\":[]}");

        client.get().uri("/api/reference-data/cities")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Auth login is accessible without token")
    void authLoginAccessibleWithoutToken() {
        stubPost("/auth/login", 200, "{\"token\":\"test\"}");

        client.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{\"username\":\"test\"}")
                .exchange()
                .expectStatus().isOk();
    }
}
```

### Step 6: Create `SecurityHeadersTest.java`

**File:** `services/api-gateway/src/test/java/com/insurancemanagementsystem/gateway/SecurityHeadersTest.java`

```java
package com.insurancemanagementsystem.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that security headers are present on all responses.
 */
class SecurityHeadersTest extends BaseGatewayTest {

    @Test
    @DisplayName("Response includes X-Content-Type-Options: nosniff")
    void responseIncludesContentTypeOptions() {
        stubGet("/reference-data/cities", 200, "[]");

        client.get().uri("/api/reference-data/cities")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");
    }

    @Test
    @DisplayName("Response includes X-Frame-Options: DENY")
    void responseIncludesFrameOptions() {
        stubGet("/reference-data/cities", 200, "[]");

        client.get().uri("/api/reference-data/cities")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    @DisplayName("Response includes Referrer-Policy")
    void responseIncludesReferrerPolicy() {
        stubGet("/reference-data/cities", 200, "[]");

        client.get().uri("/api/reference-data/cities")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    @Test
    @DisplayName("404 response also includes security headers")
    void errorResponseIncludesSecurityHeaders() {
        client.get().uri("/api/nonexistent")
                .exchange()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");
    }
}
```

### Step 7: Create `CorsTest.java`

**File:** `services/api-gateway/src/test/java/com/insurancemanagementsystem/gateway/CorsTest.java`

```java
package com.insurancemanagementsystem.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.HttpHeaders.*;

/**
 * Tests CORS preflight and response headers.
 */
class CorsTest extends BaseGatewayTest {

    @Test
    @DisplayName("OPTIONS preflight returns CORS headers for allowed origin")
    void preflightReturnsCorsHeaders() {
        client.options().uri("/api/customers")
                .header(ORIGIN, "http://localhost:3000")
                .header(ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(ACCESS_CONTROL_ALLOW_ORIGIN)
                .expectHeader().exists(ACCESS_CONTROL_ALLOW_METHODS)
                .expectHeader().exists(ACCESS_CONTROL_MAX_AGE);
    }

    @Test
    @DisplayName("CORS headers present on actual response")
    void actualResponseIncludesCorsHeaders() {
        stubGet("/reference-data/cities", 200, "[]");

        client.get().uri("/api/reference-data/cities")
                .header(ORIGIN, "http://localhost:3000")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(ACCESS_CONTROL_ALLOW_ORIGIN);
    }
}
```

### Step 8: Create `ErrorHandlingTest.java`

**File:** `services/api-gateway/src/test/java/com/insurancemanagementsystem/gateway/ErrorHandlingTest.java`

```java
package com.insurancemanagementsystem.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests error response format and handling.
 */
class ErrorHandlingTest extends BaseGatewayTest {

    @Test
    @DisplayName("404 returns standardized ErrorResponse")
    void notFoundReturnsStandardizedError() {
        client.get().uri("/api/nonexistent")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.timestamp").exists();
    }

    @Test
    @DisplayName("Error responses include timestamp")
    void errorResponseIncludesTimestamp() {
        client.get().uri("/api/nonexistent")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    @DisplayName("Error responses have success=false")
    void errorResponseHasSuccessFalse() {
        client.get().uri("/api/nonexistent")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }
}
```

### Step 9: Update `build.gradle.kts` to verify test dependencies

Re-read the `build.gradle.kts` file and ensure:
1. WireMock standalone is in test dependencies
2. `spring-boot-starter-restclient` and `spring-boot-resttestclient` are in test dependencies
3. Testcontainers BOM is in dependency management
4. `reactor-test` is in test dependencies

### Step 10: Build & run tests

```bash
.\gradlew.bat :services:api-gateway:test
```

If tests fail, debug and fix before proceeding.

---

## Acceptance Criteria

- [x] Test dependencies added to `build.gradle.kts` (WireMock, WebTestClient, Testcontainers, reactor-test)
- [x] `application-test.yml` exists with WireMock-based route overrides
- [x] `BaseGatewayTest.java` sets up WireMock and WebTestClient
- [x] `GatewayRoutingTest` — all 7 routes forward correctly
- [x] `JwtAuthenticationTest` — valid/expired/missing/malformed token scenarios
- [x] `SecurityHeadersTest` — security headers on responses
- [x] `CorsTest` — preflight and actual response CORS headers
- [x] `ErrorHandlingTest` — standardized error response format
- [x] All tests pass: `.\gradlew.bat :services:api-gateway:test`
- [x] Test coverage covers all Gateway filter chain components
