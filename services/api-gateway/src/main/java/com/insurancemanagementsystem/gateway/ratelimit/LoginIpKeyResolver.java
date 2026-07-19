package com.insurancemanagementsystem.gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * IP-only KeyResolver for the login endpoint. Always keys by client IP — never by
 * X-User-Id (user is not yet authenticated).
 */
@Component("loginIpKeyResolver")
@Slf4j
public class LoginIpKeyResolver implements KeyResolver {

	@Override
	public Mono<String> resolve(org.springframework.web.server.ServerWebExchange exchange) {
		String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
		String ip;
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			ip = forwardedFor.split(",")[0].trim();
		}
		else {
			ip = Objects
				.requireNonNullElse(exchange.getRequest().getRemoteAddress(),
						new java.net.InetSocketAddress("unknown", 0))
				.getAddress()
				.getHostAddress();
		}
		return Mono.just("login-ip:" + ip);
	}

}
