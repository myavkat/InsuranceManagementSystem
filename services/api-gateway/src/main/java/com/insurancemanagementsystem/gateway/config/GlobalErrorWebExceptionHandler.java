package com.insurancemanagementsystem.gateway.config;

import com.insurancemanagementsystem.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Global error handler for all uncaught exceptions in the Gateway filter chain.
 *
 * Executed AFTER RateLimitExceptionHandler (@Order(-2)), which handles 429 specifically.
 * This handler (@Order(-1)) is the catch-all for everything else.
 *
 * Maps exceptions to HTTP status codes and returns standardized ErrorResponse JSON.
 */
@Configuration
@Order(-1)
@Slf4j
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
		ServerHttpResponse response = exchange.getResponse();

		HttpStatus status;
		String message;

		if (ex instanceof ResponseStatusException rse) {
			status = HttpStatus.valueOf(rse.getStatusCode().value());
			message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
			if (status.is5xxServerError()) {
				log.error("Gateway error: {} - {}", status.value(), message, ex);
			}
			else {
				log.warn("Gateway client error: {} - {}", status.value(), message);
			}
		}
		else if (ex instanceof IllegalArgumentException) {
			status = HttpStatus.BAD_REQUEST;
			message = ex.getMessage();
			log.warn("Bad request: {}", message);
		}
		else {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
			message = "An unexpected error occurred";
			log.error("Unhandled gateway error", ex);
		}

		response.setStatusCode(status);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

		String errorJson = String.format("{\"success\":false,\"message\":\"%s\",\"data\":null,\"timestamp\":\"%s\"}",
				escapeJson(message), java.time.Instant.now().toString());

		byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
		DataBuffer buffer = response.bufferFactory().wrap(bytes);
		return response.writeWith(Mono.just(buffer));
	}

	private String escapeJson(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

}
