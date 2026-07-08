package com.insurancemanagementsystem.auth;

import com.insurancemanagementsystem.auth.dto.*;
import com.insurancemanagementsystem.auth.entity.Role;
import com.insurancemanagementsystem.auth.repository.RefreshTokenRepository;
import com.insurancemanagementsystem.auth.repository.RoleRepository;
import com.insurancemanagementsystem.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
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
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private final ObjectMapper objectMapper = new JsonMapper();

    // ================================================================
    // Test data
    // ================================================================

    private static final String VALID_USERNAME = "testuser";
    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password123";

    // ================================================================
    // Setup
    // ================================================================

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // Seed roles if not present (create-drop DDL does not load init.sql)
        if (roleRepository.findByName("CUSTOMER").isEmpty()) {
            roleRepository.save(Role.builder().name("CUSTOMER").build());
        }
        if (roleRepository.findByName("AGENT").isEmpty()) {
            roleRepository.save(Role.builder().name("AGENT").build());
        }
        if (roleRepository.findByName("ADMIN").isEmpty()) {
            roleRepository.save(Role.builder().name("ADMIN").build());
        }
    }

    // ================================================================
    // REGISTER tests
    // ================================================================

    @Test
    @DisplayName("POST /api/auth/register — should register a new user")
    void register_shouldCreateUser() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username(VALID_USERNAME)
                .email(VALID_EMAIL)
                .password(VALID_PASSWORD)
                .build();

        ExchangeResult result = restTestClient.post().uri("/api/auth/register")
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.username").isEqualTo(VALID_USERNAME)
                .jsonPath("$.data.email").isEqualTo(VALID_EMAIL)
                .returnResult();

        // Verify CUSTOMER role assigned
        String roles = objectMapper.readTree(result.getResponseBodyContent())
                .get("data").get("roles").toString();
        assertThat(roles).contains("CUSTOMER");
    }

    @Test
    @DisplayName("POST /api/auth/register — should reject duplicate username")
    void register_shouldRejectDuplicateUsername() {
        // First registration
        RegisterRequest request = RegisterRequest.builder()
                .username("dupuser")
                .email("first@example.com")
                .password(VALID_PASSWORD)
                .build();
        restTestClient.post().uri("/api/auth/register").body(request)
                .exchange().expectStatus().isOk();

        // Duplicate registration with same username
        RegisterRequest duplicate = RegisterRequest.builder()
                .username("dupuser")
                .email("second@example.com")
                .password(VALID_PASSWORD)
                .build();
        restTestClient.post().uri("/api/auth/register").body(duplicate)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    @DisplayName("POST /api/auth/register — should reject duplicate email")
    void register_shouldRejectDuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .username("user1")
                .email("shared@example.com")
                .password(VALID_PASSWORD)
                .build();
        restTestClient.post().uri("/api/auth/register").body(request)
                .exchange().expectStatus().isOk();

        RegisterRequest duplicate = RegisterRequest.builder()
                .username("user2")
                .email("shared@example.com")
                .password(VALID_PASSWORD)
                .build();
        restTestClient.post().uri("/api/auth/register").body(duplicate)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    @DisplayName("POST /api/auth/register — should validate required fields")
    void register_shouldValidateRequiredFields() {
        RegisterRequest request = RegisterRequest.builder()
                .username("")
                .email("not-an-email")
                .password("12")
                .build();

        restTestClient.post().uri("/api/auth/register").body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    // ================================================================
    // LOGIN tests
    // ================================================================

    @Test
    @DisplayName("POST /api/auth/login — should authenticate with valid credentials")
    void login_shouldReturnTokens() throws Exception {
        registerUser("logintest", "logintest@example.com", VALID_PASSWORD);

        LoginRequest request = LoginRequest.builder()
                .username("logintest")
                .password(VALID_PASSWORD)
                .build();

        ExchangeResult result = restTestClient.post().uri("/api/auth/login")
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .returnResult();

        var json = objectMapper.readTree(result.getResponseBodyContent()).get("data");
        assertThat(json.get("accessToken").asText()).isNotBlank();
        assertThat(json.get("refreshToken").asText()).isNotBlank();
        assertThat(json.get("expiresIn").asLong()).isGreaterThan(0);
        assertThat(json.get("tokenType").asText()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("POST /api/auth/login — should reject invalid password")
    void login_shouldRejectInvalidPassword() {
        registerUser("badpw", "badpw@example.com", VALID_PASSWORD);

        LoginRequest request = LoginRequest.builder()
                .username("badpw")
                .password("wrongpassword")
                .build();

        restTestClient.post().uri("/api/auth/login").body(request)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    @DisplayName("POST /api/auth/login — should reject unknown username")
    void login_shouldRejectUnknownUsername() {
        LoginRequest request = LoginRequest.builder()
                .username("nonexistent")
                .password(VALID_PASSWORD)
                .build();

        restTestClient.post().uri("/api/auth/login").body(request)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    @DisplayName("POST /api/auth/login — should lock account after 5 failed attempts")
    void login_shouldLockAfterMaxFailedAttempts() throws Exception {
        registerUser("locktest", "locktest@example.com", VALID_PASSWORD);

        LoginRequest badRequest = LoginRequest.builder()
                .username("locktest")
                .password("wrongpassword")
                .build();

        // 5 failed attempts
        for (int i = 0; i < 5; i++) {
            restTestClient.post().uri("/api/auth/login").body(badRequest)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false);
        }

        // 6th attempt — should be locked (not just wrong password)
        ExchangeResult lockedResp = restTestClient.post().uri("/api/auth/login")
                .body(badRequest)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .returnResult();

        String message = objectMapper.readTree(lockedResp.getResponseBodyContent())
                .get("message").asText();
        assertThat(message).containsIgnoringCase("locked");
    }

    // ================================================================
    // REFRESH tests
    // ================================================================

    @Test
    @DisplayName("POST /api/auth/refresh — should issue new tokens with valid refresh token")
    void refresh_shouldRotateTokens() throws Exception {
        registerUser("refreshtest", "refreshtest@example.com", VALID_PASSWORD);

        // Login to get tokens
        String refreshToken = loginAndGetRefreshToken("refreshtest", VALID_PASSWORD);
        String accessToken = loginAndGetAccessToken("refreshtest", VALID_PASSWORD);

        // Use refresh token
        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        ExchangeResult result = restTestClient.post().uri("/api/auth/refresh")
                .body(refreshRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .returnResult();

        var json = objectMapper.readTree(result.getResponseBodyContent()).get("data");
        assertThat(json.get("accessToken").asText()).isNotBlank();
        assertThat(json.get("accessToken").asText()).isNotEqualTo(accessToken);
        assertThat(json.get("refreshToken").asText()).isNotBlank();
        assertThat(json.get("refreshToken").asText()).isNotEqualTo(refreshToken);

        // Old refresh token should be revoked (single-use)
        ExchangeResult reuseResponse = restTestClient.post().uri("/api/auth/refresh")
                .body(refreshRequest)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .returnResult();

        String reuseMessage = objectMapper.readTree(reuseResponse.getResponseBodyContent())
                .get("message").asText();
        assertThat(reuseMessage).containsIgnoringCase("revoked");
    }

    // ================================================================
    // VALIDATE tests
    // ================================================================

    @Test
    @DisplayName("POST /api/auth/validate — should validate a valid token")
    void validate_shouldReturnValidForGoodToken() throws Exception {
        registerUser("validatetest", "validatetest@example.com", VALID_PASSWORD);
        String accessToken = loginAndGetAccessToken("validatetest", VALID_PASSWORD);

        ExchangeResult result = restTestClient.post().uri("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.valid").isEqualTo(true)
                .returnResult();

        var data = objectMapper.readTree(result.getResponseBodyContent()).get("data");
        assertThat(data.get("userId").asText()).isNotBlank();
        assertThat(data.get("roles").toString()).contains("CUSTOMER");
    }

    @Test
    @DisplayName("POST /api/auth/validate — should return invalid for missing token")
    void validate_shouldReturnInvalidForMissingToken() {
        restTestClient.post().uri("/api/auth/validate")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    @Test
    @DisplayName("POST /api/auth/validate — should return invalid for tampered token")
    void validate_shouldReturnInvalidForTamperedToken() throws Exception {
        registerUser("tampertest", "tampertest@example.com", VALID_PASSWORD);
        String accessToken = loginAndGetAccessToken("tampertest", VALID_PASSWORD);

        // Tamper with the token (change last character)
        String tamperedToken = accessToken.substring(0, accessToken.length() - 1) + "X";

        restTestClient.post().uri("/api/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
    }

    // ================================================================
    // PUBLIC KEY tests
    // ================================================================

    @Test
    @DisplayName("GET /api/auth/public-key — should return PEM public key")
    void publicKey_shouldReturnPem() throws Exception {
        ExchangeResult result = restTestClient.get().uri("/api/auth/public-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();

        String pem = new String(result.getResponseBodyContent(), StandardCharsets.UTF_8);
        assertThat(pem).contains("-----BEGIN PUBLIC KEY-----");
        assertThat(pem).contains("-----END PUBLIC KEY-----");
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
        restTestClient.post().uri("/api/auth/register").body(request)
                .exchange();
    }

    private String loginAndGetAccessToken(String username, String password) throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        ExchangeResult result = restTestClient.post().uri("/api/auth/login")
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();

        return objectMapper.readTree(result.getResponseBodyContent())
                .get("data").get("accessToken").asText();
    }

    private String loginAndGetRefreshToken(String username, String password) throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        ExchangeResult result = restTestClient.post().uri("/api/auth/login")
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult();

        return objectMapper.readTree(result.getResponseBodyContent())
                .get("data").get("refreshToken").asText();
    }
}
