package com.insurancemanagementsystem.auth.controller;

import com.insurancemanagementsystem.auth.dto.*;
import com.insurancemanagementsystem.auth.security.JwtTokenProvider;
import com.insurancemanagementsystem.auth.service.AuthService;
import com.insurancemanagementsystem.common.web.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

	private final AuthService authService;

	private final JwtTokenProvider jwtTokenProvider;

	// ================================================================
	// POST /api/auth/register
	// ================================================================

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
		log.info("Register request: username={}", request.getUsername());
		try {
			UserResponse response = authService.register(request);
			return ResponseEntity.ok(ApiResponse.success("User registered successfully", response));
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
		}
	}

	// ================================================================
	// POST /api/auth/login
	// ================================================================

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
		log.info("Login request: username={}", request.getUsername());
		try {
			LoginResponse response = authService.login(request);
			return ResponseEntity.ok(ApiResponse.success("Login successful", response));
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.status(401).body(ApiResponse.error(e.getMessage()));
		}
	}

	// ================================================================
	// POST /api/auth/refresh
	// ================================================================

	@PostMapping("/refresh")
	public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		log.info("Token refresh request");
		try {
			LoginResponse response = authService.refresh(request);
			return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.status(401).body(ApiResponse.error(e.getMessage()));
		}
	}

	// ================================================================
	// POST /api/auth/validate
	// ================================================================

	@PostMapping("/validate")
	public ResponseEntity<ApiResponse<ValidateResponse>> validate(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
		log.debug("Token validation request");
		ValidateResponse response = authService.validate(authHeader);
		if (!response.isValid()) {
			return ResponseEntity.ok(ApiResponse.error("Token is invalid"));
		}
		return ResponseEntity.ok(ApiResponse.success("Token is valid", response));
	}

	// ================================================================
	// GET /api/auth/public-key
	// ================================================================

	@GetMapping("/public-key")
	public ResponseEntity<String> getPublicKey() {
		String pem = jwtTokenProvider.getPublicKeyPem();
		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(pem);
	}

}
