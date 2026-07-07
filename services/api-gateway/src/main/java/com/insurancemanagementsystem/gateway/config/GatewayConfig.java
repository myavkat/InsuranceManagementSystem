package com.insurancemanagementsystem.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class GatewayConfig {

    /**
     * Filter chain order (defined by @Order on each GlobalFilter):
     * 1. RateLimiter (built-in Spring Cloud Gateway, order = -1) — Plan 04
     * 2. JwtAuthFilter (HIGHEST_PRECEDENCE + 100 = Integer.MIN_VALUE + 100) — Plan 03
     * 3. Route forwarding (built-in)
     * 4. Response filters (Plan 05)
     */

    /**
     * CORS configuration — allows frontend origins.
     * Plan 05 will enhance this with production domain configuration.
     */
    @Bean
    public WebFluxConfigurer corsConfigurer() {
        return new WebFluxConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:3000")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
