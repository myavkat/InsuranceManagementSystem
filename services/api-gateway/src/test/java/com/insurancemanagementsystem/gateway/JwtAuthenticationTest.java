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
