package com.insurancemanagementsystem.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
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

	public JwtTokenProvider(@Value("${auth.jwt.access-token-expiry-ms:900000}") long accessTokenExpiryMs,
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
	 * Generate an opaque refresh token (UUID as string). The caller is responsible for
	 * hashing it before storage.
	 */
	public String generateRefreshToken() {
		return UUID.randomUUID().toString();
	}

	/**
	 * Compute SHA-256 hash of a refresh token for database storage.
	 */
	public String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 not available", e);
		}
	}

	/**
	 * Validate an access token and return its claims. Returns null if the token is
	 * invalid or expired.
	 */
	public Claims validateToken(String token) {
		try {
			return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
		}
		catch (Exception e) {
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
		return "-----BEGIN PUBLIC KEY-----\n" + base64.replaceAll("(.{64})", "$1\n") + "\n-----END PUBLIC KEY-----";
	}

	private PrivateKey loadPrivateKey() {
		try {
			ClassPathResource resource = new ClassPathResource("keys/private-key.pem");
			String pemContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

			String base64Key = pemContent.replace("-----BEGIN PRIVATE KEY-----", "")
				.replace("-----END PRIVATE KEY-----", "")
				.replaceAll("\\s", "");

			byte[] keyBytes = Base64.getDecoder().decode(base64Key);
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePrivate(spec);
		}
		catch (Exception e) {
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
				String base64Key = pemContent.replace("-----BEGIN PUBLIC KEY-----", "")
					.replace("-----END PUBLIC KEY-----", "")
					.replaceAll("\\s", "");
				byte[] keyBytes = Base64.getDecoder().decode(base64Key);
				X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
				KeyFactory keyFactory = KeyFactory.getInstance("RSA");
				return keyFactory.generatePublic(spec);
			}
			// Otherwise derive from private key (the /public-key endpoint doesn't need a
			// file)
			// For now, throw — we need the matching public key file
			throw new IllegalStateException("Public key file not found at classpath:keys/public-key.pem. "
					+ "Copy it from services/api-gateway/src/main/resources/keys/public-key.pem");
		}
		catch (IllegalStateException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to load JWT public key", e);
			throw new IllegalStateException("Cannot load JWT public key", e);
		}
	}

}
