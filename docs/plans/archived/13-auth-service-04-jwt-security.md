# Plan 13-04: Auth Service — JWT Utility & Security Config

**Objective:** Create the JWT token provider utility class, the Spring Security configuration with BCrypt bean, and copy the RSA private key from the gateway to the auth service.

**Depends on:** Plan 13-01 (build file with jjwt + spring-security dependencies must exist, application.yml must exist).

**Does NOT depend on Plans 02-03** (entities/repositories are not needed for JWT and security config — these are standalone infrastructure).

**Estimated files to create:** 3
**Estimated files to copy:** 1

---

## Files to Read First

Before writing any code, open these files:

| File | Why |
|------|-----|
| `services/api-gateway/src/main/java/.../auth/JwtPublicKeyProvider.java` | Pattern for loading PEM keys from classpath |
| `services/api-gateway/src/main/java/.../auth/JwtAuthFilter.java` | See how Gateway validates tokens — auth service must produce tokens this filter can verify. Uses `Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token)` with claims `sub` (userId) and `roles` (List) |
| `services/api-gateway/src/main/resources/keys/public-key.pem` | The public key the Gateway uses — auth service must sign with the matching private key |
| `services/api-gateway/src/main/resources/keys/private-key.pem` | The private key to copy to auth-service — this is the matching keypair |
| `docs/outlines/06_API_GATEWAY_AUTH.md` | Token format spec: RSA-256, 15-min access, 7-day refresh, claims `sub`/`roles`/`iat`/`exp`/`jti`, BCrypt strength 12 |

---

## Security Specs (from outline)

| Rule | Value |
|------|-------|
| Access token algorithm | RSA-256 (RS256) |
| Access token expiry | 15 minutes |
| Refresh token type | Opaque UUID, stored as SHA-256 hash |
| Refresh token expiry | 7 days |
| Access token claims | `sub` (userId), `roles` (String array), `iat`, `exp`, `jti` |
| Password hashing | BCrypt strength 12 |

---

## Steps

### Step 1: Copy the private key from gateway to auth-service

The gateway already has a matching RSA keypair. Copy the private key so the auth service can sign JWTs that the gateway can verify.

**Source:** `services/api-gateway/src/main/resources/keys/private-key.pem`
**Destination:** `services/auth-service/src/main/resources/keys/private-key.pem`

Create the `keys/` directory under `services/auth-service/src/main/resources/` first, then copy the file.

Do NOT copy `public-key.pem` — the auth service will serve the public key via an endpoint, derived from the private key at startup. The gateway already has its own copy of the public key.

### Step 2: Create `JwtTokenProvider`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/security/JwtTokenProvider.java`

This class handles:
- Loading the RSA private key from classpath at startup
- Generating access tokens (signed JWTs)
- Generating refresh tokens (opaque UUIDs)
- Validating access tokens
- Extracting claims from tokens
- Computing SHA-256 hash of refresh tokens

```java
package com.insurancemanagementsystem.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProvider(
            @Value("${auth.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs,
            @Value("${auth.jwt.refresh-token-expiry-ms:604800000}") long refreshTokenExpiryMs) {
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
        this.privateKey = loadPrivateKey();
        this.publicKey = derivePublicKey();
        log.info("JWT keys loaded successfully");
    }

    /**
     * Generate a signed JWT access token.
     */
    public String generateAccessToken(String userId, List<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiryMs);

        return Jwts.builder()
                .subject(userId)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(privateKey)
                .compact();
    }

    /**
     * Generate an opaque refresh token (UUID as string).
     * The caller is responsible for hashing it before storage.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Compute SHA-256 hash of a refresh token for database storage.
     */
    public String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Validate an access token and return its claims.
     * Returns null if the token is invalid or expired.
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the access token expiry duration in milliseconds.
     */
    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    /**
     * Get the refresh token expiry Instant from now.
     */
    public Instant getRefreshTokenExpiry() {
        return Instant.now().plusMillis(refreshTokenExpiryMs);
    }

    /**
     * Get the public key as PEM string (for the /public-key endpoint).
     */
    public String getPublicKeyPem() {
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" +
               base64.replaceAll("(.{64})", "$1\n") +
               "\n-----END PUBLIC KEY-----";
    }

    private PrivateKey loadPrivateKey() {
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
            log.error("Failed to load JWT private key", e);
            throw new IllegalStateException("Cannot load JWT private key — auth service cannot sign tokens", e);
        }
    }

    private PublicKey derivePublicKey() {
        try {
            ClassPathResource resource = new ClassPathResource("keys/public-key.pem");
            // If public key file exists (for dev convenience), use it directly
            if (resource.exists()) {
                String pemContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String base64Key = pemContent
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] keyBytes = Base64.getDecoder().decode(base64Key);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return keyFactory.generatePublic(spec);
            }
            // Otherwise derive from private key (the /public-key endpoint doesn't need a file)
            // For now, throw — we need the matching public key file
            throw new IllegalStateException(
                "Public key file not found at classpath:keys/public-key.pem. " +
                "Copy it from services/api-gateway/src/main/resources/keys/public-key.pem");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load JWT public key", e);
            throw new IllegalStateException("Cannot load JWT public key", e);
        }
    }
}
```

Important notes:
- The public key is loaded from classpath (same PEM file the gateway uses)
- Token expiry values are configurable via `auth.jwt.access-token-expiry-ms` (default 900000 = 15 min) and `auth.jwt.refresh-token-expiry-ms` (default 604800000 = 7 days)
- The `hashToken` method uses `MessageDigest` (standard library, no extra dependency)

### Step 3: Copy the public key from gateway to auth-service

The JwtTokenProvider's `derivePublicKey()` method tries to load the public key from classpath. Copy it so both methods work.

**Source:** `services/api-gateway/src/main/resources/keys/public-key.pem`
**Destination:** `services/auth-service/src/main/resources/keys/public-key.pem`

### Step 4: Create `SecurityConfig`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/config/SecurityConfig.java`

This is a minimal Spring Security configuration — the auth service is NOT a resource server. It just needs the `BCryptPasswordEncoder` bean and permits all requests (the auth endpoints are public by design).

```java
package com.insurancemanagementsystem.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

Key points:
- `BCryptPasswordEncoder(12)` — strength 12 as specified in the outline
- CSRF disabled (stateless JWT auth)
- Session creation policy STATELESS (no HTTP sessions)
- All `/api/auth/**` endpoints are public
- Actuator health endpoints are public

### Step 5: Verify compilation

```
./gradlew :services:auth-service:compileJava
```

---

## Acceptance Criteria

- [x] `services/auth-service/src/main/resources/keys/private-key.pem` exists (copy of gateway's private key)
- [x] `services/auth-service/src/main/resources/keys/public-key.pem` exists (copy of gateway's public key)
- [x] `JwtTokenProvider.java` exists with `generateAccessToken`, `generateRefreshToken`, `hashToken`, `validateToken`, `getPublicKeyPem`, `getRefreshTokenExpiry`, `getAccessTokenExpiryMs`
- [x] `SecurityConfig.java` exists with `BCryptPasswordEncoder(12)` bean and permissive security for `/api/auth/**`
- [x] `./gradlew :services:auth-service:compileJava` succeeds
