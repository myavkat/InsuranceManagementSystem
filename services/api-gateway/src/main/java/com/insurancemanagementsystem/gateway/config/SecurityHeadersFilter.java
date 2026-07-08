package com.insurancemanagementsystem.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds security headers to all responses.
 * Executed after route forwarding (response phase) via post-filter.
 */
@Component
@Slf4j
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Set response headers BEFORE the downstream chain runs.
        // Once the ServerHttpResponse is committed, WebFlux wraps its headers in
        // ReadOnlyHttpHeaders — any attempt to mutate them after that point throws
        // UnsupportedOperationException. This has always been true in WebFlux
        // (not new to Spring 7 / Boot 4), so post-filter header modification isn't possible.
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Frame-Options", "DENY");
        headers.add("X-XSS-Protection", "0");
        headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.add("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Execute after JWT filter, before logging
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
