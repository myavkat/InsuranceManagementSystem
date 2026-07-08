package com.insurancemanagementsystem.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Base class for Gateway integration tests.
 * Starts a WireMock server to simulate downstream microservices.
 * All routes are configured to forward to this WireMock server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(BaseGatewayTest.TestCorsConfiguration.class)
public abstract class BaseGatewayTest {

    /**
     * Test-only CORS configuration using CorsWebFilter, which runs at the WebFilter
     * level (before SCG route matching). This handles CORS preflight requests
     * correctly with Spring Cloud Gateway.
     */
    @TestConfiguration
    static class TestCorsConfiguration {

        @Bean
        CorsWebFilter corsWebFilter() {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of("http://localhost:3000"));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/**", config);
            return new CorsWebFilter(source);
        }
    }

    protected static WireMockServer wireMockServer;

    @Autowired
    protected WebTestClient client;

    @LocalServerPort
    private int port;

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
     * Shortcut to set up a WireMock stub for GET requests.
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

    /**
     * Shortcut to set up a WireMock stub for POST requests.
     */
    protected void stubPost(String path, int status, String body) {
        wireMockServer.stubFor(
                WireMock.post(WireMock.urlPathEqualTo(path))
                        .willReturn(WireMock.aResponse()
                                .withStatus(status)
                                .withHeader("Content-Type", "application/json")
                                .withBody(body))
        );
    }

    /**
     * Shortcut to set up a WireMock stub for OPTIONS requests with CORS headers.
     */
    protected void stubOptionsWithCors(String path) {
        wireMockServer.stubFor(
                WireMock.options(WireMock.urlPathEqualTo(path))
                        .willReturn(WireMock.aResponse()
                                .withStatus(200)
                                .withHeader("Access-Control-Allow-Origin", "http://localhost:3000")
                                .withHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,PATCH,OPTIONS")
                                .withHeader("Access-Control-Max-Age", "3600"))
        );
    }
}
