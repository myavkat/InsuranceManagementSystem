package com.insurancemanagementsystem.gateway.auth;

import com.insurancemanagementsystem.gateway.dto.ErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * Global filter that validates JWT tokens on authenticated routes.
 *
 * Whitelisted routes (metadata.auth-required=false in application.yml) are skipped. On
 * success, injects X-User-Id and X-User-Roles headers into downstream request. On
 * failure, returns 401 with standardized ErrorResponse JSON.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter implements GlobalFilter, Ordered {

	private final JwtPublicKeyProvider keyProvider;

	private static final String BEARER_PREFIX = "Bearer ";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);

		// 1. Check if route requires auth
		if (route == null || !isAuthRequired(route)) {
			return chain.filter(exchange);
		}

		// 2. Extract Authorization header
		String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
			return unauthorized(exchange, "Missing or invalid Authorization header");
		}

		String token = authHeader.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			return unauthorized(exchange, "Missing or invalid Authorization header");
		}

		// 3. Validate token
		try {
			Claims claims = Jwts.parser()
				.verifyWith(keyProvider.getPublicKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();

			// 4. Extract claims
			String userId = claims.getSubject();
			@SuppressWarnings("unchecked")
			List<String> roles = claims.get("roles", List.class);
			String rolesHeader = roles != null ? String.join(",", roles) : "";

			// 5. Inject headers into downstream request
			ServerHttpRequest mutatedRequest = exchange.getRequest()
				.mutate()
				.header("X-User-Id", userId)
				.header("X-User-Roles", rolesHeader)
				.build();

			ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

			log.debug("JWT validated for user={}, roles={}", userId, rolesHeader);
			return chain.filter(mutatedExchange);

		}
		catch (ExpiredJwtException e) {
			log.debug("JWT expired: {}", e.getMessage());
			return unauthorized(exchange, "Token expired");
		}
		catch (JwtException e) {
			log.debug("JWT validation failed: {}", e.getMessage());
			return unauthorized(exchange, "Invalid token signature");
		}
		catch (Exception e) {
			log.error("Unexpected JWT validation error", e);
			return unauthorized(exchange, "Authentication failed");
		}
	}

	@Override
	public int getOrder() {
		// Execute early but after rate limiter (which uses -1 by default in Spring Cloud
		// Gateway)
		return Ordered.HIGHEST_PRECEDENCE + 100;
	}

	/**
	 * Checks the route metadata to determine if auth is required. Routes without explicit
	 * metadata.auth-required are treated as requiring auth (secure by default).
	 */
	private boolean isAuthRequired(Route route) {
		Object authRequired = route.getMetadata().get("auth-required");
		if (authRequired instanceof Boolean b) {
			return b;
		}
		// Secure by default: if metadata key is missing, require auth
		return true;
	}

	/**
	 * Writes a 401 JSON error response and short-circuits the filter chain.
	 */
	private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
		exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

		ErrorResponse error = ErrorResponse.of(message);
		byte[] bytes;
		try {
			// Manual JSON serialization to avoid ObjectMapper dependency
			String json = String.format("{\"success\":false,\"message\":\"%s\",\"data\":null,\"timestamp\":\"%s\"}",
					escapeJson(message), java.time.Instant.now().toString());
			bytes = json.getBytes(StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			bytes = "{\"success\":false,\"message\":\"Internal error\"}".getBytes(StandardCharsets.UTF_8);
		}

		DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}

	/**
	 * Minimal JSON string escaping for error messages.
	 */
	private String escapeJson(String s) {
		return s.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

}
