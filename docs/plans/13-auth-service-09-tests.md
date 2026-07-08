# Plan 13-09: Auth Service — Integration Tests

**Objective:** Write integration tests for the auth service covering registration, login, token refresh, token validation, the public-key endpoint, login lockout behavior, and error scenarios.

**Depends on:** Plans 13-01 through 13-07 (all service code must be complete). Plan 13-08 (Docker Compose) is NOT a dependency for tests — tests use Testcontainers, not Docker Compose.

**Estimated files to create:** 1 test class

---

## Files to Read First

Before writing any code, open these files:

| File | Why |
|------|-----|
| `docs/outlines/11_TESTING_CONVENTIONS.md` | Spring Boot 4 testing rules: `RestTestClient`, slice/integration tests, `@SpringBootTest`, `@Testcontainers`, `PostgreSQLContainer` |
| `docs/outlines/12_DEVELOPER_COMMANDS.md` | How to run tests: `./gradlew :services:auth-service:test` |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Testcontainers on Windows — Docker Desktop WSL2 pipe configuration |
| `services/auth-service/src/main/java/.../controller/AuthController.java` | Know the endpoint paths, request/response shapes |
| `services/auth-service/src/main/java/.../service/AuthService.java` | Know the behavior to test |
| `infra/sql/auth_db/init.sql` | Know the seed data: admin user already exists |

---

## Testing Conventions Reference

From `docs/outlines/11_TESTING_CONVENTIONS.md`:
- Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` for full integration tests
- Use `RestTestClient` (Spring Boot 4) for HTTP calls — `org.springframework.boot.resttestclient.RestTestClient`
- Use `@Testcontainers` with `PostgreSQLContainer` for database
- Use `@DynamicPropertySource` to override datasource properties for Testcontainers
- Tests should be in the same package structure as the main code

---

## Steps

### Step 1: Create test configuration

**File:** `services/auth-service/src/test/resources/application-test.yml`

```yaml
# Test configuration overrides
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auth_db_test
    username: test
    password: test
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  cloud:
    stream:
      enabled: false

auth:
  jwt:
    access-token-expiry-ms: 5000       # 5 seconds for testing expiry
    refresh-token-expiry-ms: 10000     # 10 seconds for testing expiry

logging:
  level:
    com.insurancemanagementsystem: DEBUG
```

The `spring.cloud.stream.enabled: false` disables Kafka (auth-service has no bindings, but it prevents connection attempts).

### Step 2: Create the test class

**File:** `services/auth-service/src/test/java/com/insurancemanagementsystem/auth/AuthControllerIntegrationTest.java`

```java
package com.insurancemanagementsystem.auth;

import com.insurancemanagementsystem.auth.dto.*;
import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.RestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_db_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    // ================================================================
    // Test data
    // ================================================================

    private static final String VALID_USERNAME = "testuser";
    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password123";

    // ================================================================
    // REGISTER tests
    // ================================================================

    @Test
    @DisplayName("POST /api/auth/register — should register a new user")
    void register_shouldCreateUser() {
        RegisterRequest request = RegisterRequest.builder()
                .username(VALID_USERNAME)
                .email(VALID_EMAIL)
                .password(VALID_PASSWORD)
                .build();

        ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                "/api/auth/register",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getUsername()).isEqualTo(VALID_USERNAME);
        assertThat(response.getBody().getData().getEmail()).isEqualTo(VALID_EMAIL);
        assertThat(response.getBody().getData().getRoles()).contains("CUSTOMER");
    }

    @Test
    @DisplayName("POST /api/auth/register — should reject duplicate username")
    void register_shouldRejectDuplicateUsername() {
        // First registration
        RegisterRequest request = RegisterRequest.builder()
                .username(VALID_USERNAME + "_dup")
                .email("dup1@example.com")
                .password(VALID_PASSWORD)
                .build();
        restTemplate.postForEntity("/api/auth/register", request, ApiResponse.class);

        // Duplicate registration with same username
        RegisterRequest duplicate = RegisterRequest.builder()
                .username(VALID_USERNAME + "_dup")
                .email("dup2@example.com")
                .password(VALID_PASSWORD)
                .build();

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/auth/register", duplicate, ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("POST /api/auth/register — should validate required fields")
    void register_shouldValidateRequiredFields() {
        RegisterRequest request = RegisterRequest.builder()
                .username("")
                .email("not-an-email")
                .password("12")
                .build();

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/auth/register", request, ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    // ================================================================
    // LOGIN tests
    // ================================================================

    @Test
    @DisplayName("POST /api/auth/login — should authenticate with valid credentials")
    void login_shouldReturnTokens() {
        // Register first
        registerUser("logintest", "logintest@example.com", VALID_PASSWORD);

        // Login
        LoginRequest request = LoginRequest.builder()
                .username("logintest")
                .password(VALID_PASSWORD)
                .build();

        ResponseEntity<ApiResponse<LoginResponse>> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getAccessToken()).isNotBlank();
        assertThat(response.getBody().getData().getRefreshToken()).isNotBlank();
        assertThat(response.getBody().getData().getExpiresIn()).isGreaterThan(0);
        assertThat(response.getBody().getData().getTokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("POST /api/auth/login — should reject invalid password")
    void login_shouldRejectInvalidPassword() {
        registerUser("badpw", "badpw@example.com", VALID_PASSWORD);

        LoginRequest request = LoginRequest.builder()
                .username("badpw")
                .password("wrongpassword")
                .build();

        ResponseEntity<ApiResponse> response = restTemplate.postForEntity(
                "/api/auth/login", request, ApiResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("POST /api/auth/login — should lock account after 5 failed attempts")
    void login_shouldLockAfterMaxFailedAttempts() {
        registerUser("locktest", "locktest@example.com", VALID_PASSWORD);

        LoginRequest badRequest = LoginRequest.builder()
                .username("locktest")
                .password("wrongpassword")
                .build();

        // 5 failed attempts
        for (int i = 0; i < 5; i++) {
            ResponseEntity<ApiResponse> resp = restTemplate.postForEntity(
                    "/api/auth/login", badRequest, ApiResponse.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // 6th attempt — should be locked (not just wrong password)
        ResponseEntity<ApiResponse> lockedResp = restTemplate.postForEntity(
                "/api/auth/login", badRequest, ApiResponse.class);

        assertThat(lockedResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(lockedResp.getBody()).isNotNull();
        assertThat(lockedResp.getBody().getMessage()).containsIgnoringCase("locked");
    }

    // ================================================================
    // REFRESH tests
    // ================================================================

    @Test
    @DisplayName("POST /api/auth/refresh — should issue new tokens with valid refresh token")
    void refresh_shouldRotateTokens() throws Exception {
        registerUser("refreshtest", "refreshtest@example.com", VALID_PASSWORD);

        // Login to get tokens
        LoginResponse tokens = loginUser("refreshtest", VALID_PASSWORD);

        // Use refresh token
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(tokens.getRefreshToken())
                .build();

        ResponseEntity<ApiResponse<LoginResponse>> response = restTemplate.exchange(
                "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(refreshRequest),
                new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getAccessToken()).isNotBlank();
        assertThat(response.getBody().getData().getAccessToken()).isNotEqualTo(tokens.getAccessToken());
        assertThat(response.getBody().getData().getRefreshToken()).isNotBlank();
        assertThat(response.getBody().getData().getRefreshToken()).isNotEqualTo(tokens.getRefreshToken());

        // Old refresh token should be revoked (single-use)
        ResponseEntity<ApiResponse> reuseResponse = restTemplate.exchange(
                "/api/auth/refresh",
                HttpMethod.POST,
                new HttpEntity<>(refreshRequest),
                new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {}
        );

        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ================================================================
    // VALIDATE tests
    // ================================================================

    @Test
    @DisplayName("POST /api/auth/validate — should validate a valid token")
    void validate_shouldReturnValidForGoodToken() {
        registerUser("validatetest", "validatetest@example.com", VALID_PASSWORD);
        LoginResponse tokens = loginUser("validatetest", VALID_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.getAccessToken());

        ResponseEntity<ApiResponse<ValidateResponse>> response = restTemplate.exchange(
                "/api/auth/validate",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<ValidateResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().isValid()).isTrue();
        assertThat(response.getBody().getData().getUserId()).isNotBlank();
        assertThat(response.getBody().getData().getRoles()).contains("CUSTOMER");
    }

    @Test
    @DisplayName("POST /api/auth/validate — should return invalid for missing token")
    void validate_shouldReturnInvalidForMissingToken() {
        ResponseEntity<ApiResponse<ValidateResponse>> response = restTemplate.exchange(
                "/api/auth/validate",
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<ApiResponse<ValidateResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().isValid()).isFalse();
    }

    @Test
    @DisplayName("POST /api/auth/validate — should return invalid for tampered token")
    void validate_shouldReturnInvalidForTamperedToken() {
        registerUser("tampertest", "tampertest@example.com", VALID_PASSWORD);
        LoginResponse tokens = loginUser("tampertest", VALID_PASSWORD);

        // Tamper with the token (change last character)
        String tamperedToken = tokens.getAccessToken().substring(0,
                tokens.getAccessToken().length() - 1) + "X";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tamperedToken);

        ResponseEntity<ApiResponse<ValidateResponse>> response = restTemplate.exchange(
                "/api/auth/validate",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<ValidateResponse>>() {}
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().isValid()).isFalse();
    }

    // ================================================================
    // PUBLIC KEY tests
    // ================================================================

    @Test
    @DisplayName("GET /api/auth/public-key — should return PEM public key")
    void publicKey_shouldReturnPem() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/auth/public-key", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("-----BEGIN PUBLIC KEY-----");
        assertThat(response.getBody()).contains("-----END PUBLIC KEY-----");
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private void registerUser(String username, String email, String password) {
        RegisterRequest request = RegisterRequest.builder()
                .username(username)
                .email(email)
                .password(password)
                .build();
        restTemplate.postForEntity("/api/auth/register", request, ApiResponse.class);
    }

    private LoginResponse loginUser(String username, String password) {
        LoginRequest request = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();
        ResponseEntity<ApiResponse<LoginResponse>> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<LoginResponse>>() {}
        );
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        return response.getBody().getData();
    }
}
```

Important testing notes:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` starts the full Spring context on a random port
- `@Testcontainers` + `PostgreSQLContainer` provides a real PostgreSQL database
- `@DynamicPropertySource` overrides datasource properties to point to the container
- `create-drop` DDL means tables are created fresh + dropped after tests
- The test uses `TestRestTemplate` (auto-configured by `@SpringBootTest`)
- `ParameterizedTypeReference` is needed for generic types like `ApiResponse<LoginResponse>`
- No actual RSA keys are needed — the keys are loaded from classpath (copied in Plan 04)
- Each test method uses a unique username to avoid collisions

### Step 3: Verify tests compile

```
./gradlew :services:auth-service:compileTestJava
```

### Step 4: Run the tests

```
./gradlew :services:auth-service:test
```

If tests fail with Docker connectivity issues on Windows, see `docs/outlines/13_ENVIRONMENT_QUIRKS.md` for the Testcontainers WSL2 configuration.

---

## Acceptance Criteria

- [ ] `application-test.yml` exists with `create-drop` DDL, disabled cloud stream, short token expiries
- [ ] `AuthControllerIntegrationTest.java` exists with all test methods
- [ ] Registration tests: success, duplicate rejection, field validation
- [ ] Login tests: success with tokens, invalid password rejection, account lockout after 5 failures
- [ ] Refresh tests: token rotation, old token revocation
- [ ] Validate tests: valid token, missing token, tampered token
- [ ] Public key test: PEM format returned
- [ ] `./gradlew :services:auth-service:compileTestJava` succeeds
- [ ] `./gradlew :services:auth-service:test` passes (all tests green)
