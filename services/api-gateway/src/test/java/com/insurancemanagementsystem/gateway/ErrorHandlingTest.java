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
