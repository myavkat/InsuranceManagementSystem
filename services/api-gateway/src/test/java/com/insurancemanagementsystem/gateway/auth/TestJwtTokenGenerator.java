package com.insurancemanagementsystem.gateway.auth;

import io.jsonwebtoken.Jwts;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Test utility for generating signed JWTs using the dev private key.
 * USAGE: Only in test scope. Used by Plan 06 integration tests.
 */
public class TestJwtTokenGenerator {

    private static volatile PrivateKey cachedPrivateKey;

    public static PrivateKey getPrivateKey() {
        if (cachedPrivateKey == null) {
            synchronized (TestJwtTokenGenerator.class) {
                if (cachedPrivateKey == null) {
                    cachedPrivateKey = loadPrivateKey();
                }
            }
        }
        return cachedPrivateKey;
    }

    private static PrivateKey loadPrivateKey() {
        try {
            ClassPathResource resource = new ClassPathResource("keys/private-key.pem");
            String pemContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String base64Key = pemContent
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test private key", e);
        }
    }

    /**
     * Creates a valid JWT with the given claims, signed with the dev private key.
     */
    public static String createToken(UUID userId, List<String> roles, Instant expiration) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration))
                .id(UUID.randomUUID().toString())
                .signWith(getPrivateKey())
                .compact();
    }

    /**
     * Creates a valid JWT expiring in 15 minutes (standard access token lifetime).
     */
    public static String createValidToken(UUID userId, List<String> roles) {
        return createToken(userId, roles, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    /**
     * Creates an expired JWT (for testing 401 on expiry).
     */
    public static String createExpiredToken(UUID userId, List<String> roles) {
        return createToken(userId, roles, Instant.now().minus(1, ChronoUnit.MINUTES));
    }
}
