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
