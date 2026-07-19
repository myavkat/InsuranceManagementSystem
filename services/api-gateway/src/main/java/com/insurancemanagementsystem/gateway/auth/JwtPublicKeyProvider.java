package com.insurancemanagementsystem.gateway.auth;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RSA public key for JWT verification.
 *
 * In development: reads from classpath keys/public-key.pem. In production: will be
 * fetched from Auth Service's /api/auth/public-key endpoint and refreshed periodically.
 */
@Component
@Slf4j
public class JwtPublicKeyProvider {

	private volatile PublicKey cachedPublicKey;

	@Value("${gateway.auth.public-key-location:classpath:keys/public-key.pem}")
	private String publicKeyLocation;

	/**
	 * Returns the cached public key, loading it on first call. Thread-safe via volatile
	 * read + synchronized load.
	 */
	public PublicKey getPublicKey() {
		if (cachedPublicKey == null) {
			synchronized (this) {
				if (cachedPublicKey == null) {
					cachedPublicKey = loadPublicKey();
				}
			}
		}
		return cachedPublicKey;
	}

	private PublicKey loadPublicKey() {
		try {
			// Read PEM from classpath
			ClassPathResource resource = new ClassPathResource("keys/public-key.pem");
			String pemContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

			// Strip PEM headers/footers
			String base64Key = pemContent.replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "")
				.replaceAll("\\s", "");

			byte[] keyBytes = Base64.getDecoder().decode(base64Key);
			X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			PublicKey key = keyFactory.generatePublic(spec);
			log.info("JWT public key loaded successfully from classpath:keys/public-key.pem");
			return key;
		}
		catch (Exception e) {
			log.error("Failed to load JWT public key", e);
			throw new IllegalStateException("Cannot load JWT public key — gateway cannot validate tokens", e);
		}
	}

}
