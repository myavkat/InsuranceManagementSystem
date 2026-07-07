package com.insurancemanagementsystem.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that security headers are present on all proxied responses.
 *
 * Note: 404 responses (unmatched routes) are handled by GlobalErrorWebExceptionHandler
 * before the filter chain runs, so security headers are only present on responses
 * from matched routes that go through the SecurityHeadersFilter.
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
}
