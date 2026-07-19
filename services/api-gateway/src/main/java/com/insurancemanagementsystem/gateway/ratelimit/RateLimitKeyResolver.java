package com.insurancemanagementsystem.gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Composite KeyResolver for rate limiting.
 *
 * Priority: 1. X-User-Id header (authenticated user) — set by JwtAuthFilter 2. Client IP
 * address (unauthenticated or fallback)
 *
 * The key format distinguishes the source: - "user:<uuid>" for authenticated requests -
 * "ip:<address>" for unauthenticated requests
 */
@Primary
@Component
@Slf4j
public class RateLimitKeyResolver implements KeyResolver {

	@Override
	public Mono<String> resolve(org.springframework.web.server.ServerWebExchange exchange) {
		// 1. Try X-User-Id (authenticated user)
		String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
		if (userId != null && !userId.isBlank()) {
			return Mono.just("user:" + userId);
		}

		// 2. Fall back to client IP
		String ip = getClientIp(exchange);
		return Mono.just("ip:" + ip);
	}

	/**
	 * Resolves client IP from X-Forwarded-For or remote address.
	 */
	private String getClientIp(org.springframework.web.server.ServerWebExchange exchange) {
		String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			// Take the first IP in the chain (original client)
			return forwardedFor.split(",")[0].trim();
		}
		// Fall back to direct remote address
		return Objects
			.requireNonNullElse(exchange.getRequest().getRemoteAddress(), new java.net.InetSocketAddress("unknown", 0))
			.getAddress()
			.getHostAddress();
	}

}
