package com.insurancemanagementsystem.auth.service;

import com.insurancemanagementsystem.auth.dto.*;
import com.insurancemanagementsystem.auth.entity.RefreshToken;
import com.insurancemanagementsystem.auth.entity.Role;
import com.insurancemanagementsystem.auth.entity.User;
import com.insurancemanagementsystem.auth.repository.RefreshTokenRepository;
import com.insurancemanagementsystem.auth.repository.RoleRepository;
import com.insurancemanagementsystem.auth.repository.UserRepository;
import com.insurancemanagementsystem.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

	private final UserRepository userRepository;

	private final RoleRepository roleRepository;

	private final RefreshTokenRepository refreshTokenRepository;

	private final JwtTokenProvider jwtTokenProvider;

	private final PasswordEncoder passwordEncoder;

	private static final int MAX_FAILED_ATTEMPTS = 5;

	private static final long LOCK_DURATION_MINUTES = 15;

	// ================================================================
	// REGISTER
	// ================================================================

	@Transactional
	public UserResponse register(RegisterRequest request) {
		// 1. Validate uniqueness
		if (userRepository.existsByUsername(request.getUsername())) {
			throw new IllegalArgumentException("Username already taken");
		}
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new IllegalArgumentException("Email already registered");
		}

		// 2. Create user
		Role customerRole = roleRepository.findByName("CUSTOMER")
			.orElseThrow(() -> new IllegalStateException("Default CUSTOMER role not found in database"));

		User user = User.builder()
			.username(request.getUsername())
			.email(request.getEmail())
			.passwordHash(passwordEncoder.encode(request.getPassword()))
			.enabled(true)
			.accountNonLocked(true)
			.failedAttempts(0)
			.roles(Set.of(customerRole))
			.build();

		user = userRepository.save(user);
		log.info("User registered: username={}, id={}", user.getUsername(), user.getId());

		// 3. Return response
		return toUserResponse(user);
	}

	// ================================================================
	// LOGIN
	// ================================================================

	public LoginResponse login(LoginRequest request) {
		// 1. Find user
		User user = userRepository.findByUsername(request.getUsername())
			.orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

		// 2. Check if account is locked
		if (!user.getAccountNonLocked()) {
			// Check if lock has expired
			if (user.getLockTime() != null && user.getLockTime()
				.plus(java.time.Duration.ofMinutes(LOCK_DURATION_MINUTES))
				.isBefore(Instant.now())) {
				// Auto-unlock
				user.setAccountNonLocked(true);
				user.setFailedAttempts(0);
				user.setLockTime(null);
				userRepository.save(user);
				log.info("Account unlocked automatically: username={}", user.getUsername());
			}
			else {
				throw new IllegalArgumentException("Account is locked. Try again later.");
			}
		}

		// 3. Verify password
		if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			// Increment failed attempts
			user.setFailedAttempts(user.getFailedAttempts() + 1);
			if (user.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
				user.setAccountNonLocked(false);
				user.setLockTime(Instant.now());
				log.warn("Account locked after {} failed attempts: username={}", user.getFailedAttempts(),
						user.getUsername());
			}
			userRepository.save(user);
			throw new IllegalArgumentException("Invalid username or password");
		}

		// 4. Reset failed attempts on successful login
		user.setFailedAttempts(0);
		user.setLockTime(null);
		userRepository.save(user);

		// 5. Generate tokens
		return generateTokenPair(user);
	}

	// ================================================================
	// REFRESH
	// ================================================================

	@Transactional
	public LoginResponse refresh(RefreshTokenRequest request) {
		// 1. Hash the incoming refresh token
		String tokenHash = jwtTokenProvider.hashToken(request.getRefreshToken());

		// 2. Look up by hash
		RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
			.orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

		// 3. Check if revoked
		if (storedToken.getRevoked()) {
			// Token was already used — revoke all tokens for this user (potential theft)
			refreshTokenRepository.revokeAllForUser(storedToken.getUser().getId());
			throw new IllegalArgumentException("Refresh token has been revoked");
		}

		// 4. Check expiry
		if (storedToken.getExpiresAt().isBefore(Instant.now())) {
			throw new IllegalArgumentException("Refresh token has expired");
		}

		// 5. Revoke old token (single-use rotation)
		storedToken.setRevoked(true);
		refreshTokenRepository.save(storedToken);

		// 6. Issue new token pair
		return generateTokenPair(storedToken.getUser());
	}

	// ================================================================
	// VALIDATE
	// ================================================================

	@Transactional(readOnly = true)
	public ValidateResponse validate(String authHeader) {
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return ValidateResponse.builder().valid(false).build();
		}

		String token = authHeader.substring("Bearer ".length()).trim();
		Claims claims = jwtTokenProvider.validateToken(token);

		if (claims == null) {
			return ValidateResponse.builder().valid(false).build();
		}

		String userId = claims.getSubject();
		@SuppressWarnings("unchecked")
		List<String> roles = claims.get("roles", List.class);

		// Optional: verify user still exists and is enabled
		if (userId != null) {
			try {
				UUID userUuid = UUID.fromString(userId);
				User user = userRepository.findById(userUuid).orElse(null);
				if (user == null || !user.getEnabled() || !user.getAccountNonLocked()) {
					return ValidateResponse.builder().valid(false).build();
				}
			}
			catch (IllegalArgumentException e) {
				return ValidateResponse.builder().valid(false).build();
			}
		}

		return ValidateResponse.builder().valid(true).userId(userId).roles(roles != null ? roles : List.of()).build();
	}

	// ================================================================
	// PRIVATE HELPERS
	// ================================================================

	private LoginResponse generateTokenPair(User user) {
		List<String> roles = user.getRoles().stream().map(Role::getName).toList();

		String accessToken = jwtTokenProvider.generateAccessToken(user.getId().toString(), roles);
		String refreshToken = jwtTokenProvider.generateRefreshToken();

		// Store refresh token hash
		RefreshToken tokenEntity = RefreshToken.builder()
			.user(user)
			.tokenHash(jwtTokenProvider.hashToken(refreshToken))
			.expiresAt(jwtTokenProvider.getRefreshTokenExpiry())
			.revoked(false)
			.build();
		refreshTokenRepository.save(tokenEntity);

		return LoginResponse.builder()
			.accessToken(accessToken)
			.refreshToken(refreshToken)
			.expiresIn(jwtTokenProvider.getAccessTokenExpiryMs() / 1000) // convert ms to
																			// seconds
			.tokenType("Bearer")
			.build();
	}

	private UserResponse toUserResponse(User user) {
		return UserResponse.builder()
			.userId(user.getId().toString())
			.username(user.getUsername())
			.email(user.getEmail())
			.roles(user.getRoles().stream().map(Role::getName).toList())
			.build();
	}

}
