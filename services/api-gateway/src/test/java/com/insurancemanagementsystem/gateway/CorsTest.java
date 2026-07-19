package com.insurancemanagementsystem.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.HttpHeaders.*;

/**
 * Tests CORS preflight and response headers.
 *
 * CORS preflight handling is provided by the CorsWebFilter (configured in
 * BaseGatewayTest), which intercepts preflight requests at the WebFilter level before SCG
 * route matching. Actual response CORS headers come from downstream services and are
 * forwarded by the Gateway.
 */
class CorsTest extends BaseGatewayTest {

	@Test
	@DisplayName("OPTIONS preflight returns CORS headers for allowed origin")
	void preflightReturnsCorsHeaders() {
		client.options()
			.uri("/api/customers")
			.header(ORIGIN, "http://localhost:3000")
			.header(ACCESS_CONTROL_REQUEST_METHOD, "GET")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.exists(ACCESS_CONTROL_ALLOW_ORIGIN)
			.expectHeader()
			.exists(ACCESS_CONTROL_ALLOW_METHODS)
			.expectHeader()
			.exists(ACCESS_CONTROL_MAX_AGE);
	}

	@Test
	@DisplayName("CORS headers present on actual response from downstream")
	void actualResponseIncludesCorsHeaders() {
		wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock
			.get(com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/reference-data/cities"))
			.willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withHeader("Access-Control-Allow-Origin", "http://localhost:3000")
				.withBody("[]")));

		client.get()
			.uri("/api/reference-data/cities")
			.header(ORIGIN, "http://localhost:3000")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.exists(ACCESS_CONTROL_ALLOW_ORIGIN);
	}

}
