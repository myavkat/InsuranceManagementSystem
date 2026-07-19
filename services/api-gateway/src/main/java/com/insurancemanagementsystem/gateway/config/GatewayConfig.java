package com.insurancemanagementsystem.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class GatewayConfig {

	@Value("${gateway.cors.allowed-origins:http://localhost:3000}")
	private String allowedOriginsConfig;

	/**
	 * Filter chain order (defined by @Order on each GlobalFilter): 1. RateLimiter
	 * (built-in Spring Cloud Gateway, order = -1) — Plan 04 2. JwtAuthFilter
	 * (HIGHEST_PRECEDENCE + 100 = Integer.MIN_VALUE + 100) — Plan 03 3. Route forwarding
	 * (built-in) 4. SecurityHeadersFilter (HIGHEST_PRECEDENCE + 200) — adds security
	 * headers to responses 5. RequestLoggingFilter (HIGHEST_PRECEDENCE + 300) — logs
	 * request/response
	 */

	/**
	 * CORS configuration — allows frontend origins (dev + production). Origins are
	 * configurable via GATEWAY_CORS_ORIGINS env var (comma-separated).
	 */
	@Bean
	public WebFluxConfigurer corsConfigurer() {
		return new WebFluxConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				List<String> origins = parseOrigins(allowedOriginsConfig);
				registry.addMapping("/api/**")
					.allowedOrigins(origins.toArray(new String[0]))
					.allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
					.allowedHeaders("*")
					.exposedHeaders("X-Request-Id", "Retry-After")
					.allowCredentials(true)
					.maxAge(3600);
			}
		};
	}

	private List<String> parseOrigins(String config) {
		if (config == null || config.isBlank()) {
			return List.of("http://localhost:3000");
		}
		return Arrays.stream(config.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

}
