package com.insurancemanagementsystem.gateway.ratelimit;

import com.insurancemanagementsystem.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Overrides Gateway's default 429 response to return standardized ErrorResponse JSON
 * with Retry-After header.
 *
 * This handler catches 429 errors thrown by the RequestRateLimiter filter, which
 * can manifest as either {@link ResponseStatusException} (when throwOnLimit is false)
 * or {@link HttpClientErrorException.TooManyRequests} (when throwOnLimit is true).
 */
@Configuration
@Order(-2) // Before default error handlers
@Slf4j
public class RateLimitExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (isRateLimitException(ex)) {
            return handleRateLimit(response);
        }

        // Fall through to next error handler for non-rate-limit errors
        return Mono.error(ex);
    }

    /**
     * Checks whether the given throwable represents a rate-limit (429) error
     * from the RequestRateLimiter filter.
     */
    private boolean isRateLimitException(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return rse.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
        }
        return ex instanceof HttpClientErrorException.TooManyRequests;
    }

    private Mono<Void> handleRateLimit(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Extract retry-after from response headers set by the rate limiter
        String retryAfter = response.getHeaders().getFirst("X-RateLimit-Retry-After-Seconds");
        if (retryAfter != null) {
            response.getHeaders().set("Retry-After", retryAfter);
        } else {
            response.getHeaders().set("Retry-After", "60");
        }

        String errorJson = String.format(
                "{\"success\":false,\"message\":\"Rate limit exceeded. Try again in %s seconds.\",\"data\":null,\"timestamp\":\"%s\"}",
                retryAfter != null ? retryAfter : "60",
                java.time.Instant.now().toString()
        );

        byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
