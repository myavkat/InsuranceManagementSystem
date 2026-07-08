# Plan 13-07: Auth Service — Controller

**Objective:** Create the `AuthController` REST controller with all 5 endpoints: register, login, refresh, validate, and public-key.

**Depends on:** Plan 13-06 (AuthService must exist with all methods). All prior plans must be complete.

**Estimated files to create:** 1

---

## Files to Read First

Before writing any code, open these files:

| File | Why |
|------|-----|
| `services/reference-data-service/src/main/java/.../controller/ReferenceDataController.java` | Controller pattern: `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor`, returns `ResponseEntity<ApiResponse<T>>` |
| `services/auth-service/src/main/java/.../service/AuthService.java` | Know the exact method signatures to call |
| `services/auth-service/src/main/java/.../dto/*.java` | Know the DTO shapes for `@RequestBody` and return types |
| `services/auth-service/src/main/java/.../security/JwtTokenProvider.java` | `getPublicKeyPem()` for the public-key endpoint |
| `common/common-web/src/main/java/.../dto/ApiResponse.java` | `ApiResponse.success()`, `ApiResponse.error()` static methods |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Line 26-34: Auth service endpoints listing |

---

## Endpoint Specifications

All endpoints are under base path `/api/auth`:

| Method | Path | Request Body | Auth Header | Service Method | Success Response |
|--------|------|-------------|-------------|----------------|-----------------|
| POST | `/register` | `RegisterRequest` | — | `register()` | `ApiResponse<UserResponse>` |
| POST | `/login` | `LoginRequest` | — | `login()` | `ApiResponse<LoginResponse>` |
| POST | `/refresh` | `RefreshTokenRequest` | — | `refresh()` | `ApiResponse<LoginResponse>` |
| POST | `/validate` | — | `Authorization: Bearer <token>` | `validate()` | `ApiResponse<ValidateResponse>` |
| GET | `/public-key` | — | — | `jwtTokenProvider.getPublicKeyPem()` | PEM string (plain text) |

Note on `/validate`: The token is extracted from the `Authorization` header, NOT from the request body. The Gateway sends the full `Authorization: Bearer <token>` header when calling this endpoint as a fallback.

Note on `/public-key`: This returns plain text PEM, NOT wrapped in `ApiResponse`. The Gateway fetches this as a raw PEM string.

---

## Steps

### Step 1: Create `AuthController`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/controller/AuthController.java`

Follow the pattern from `ReferenceDataController.java`: `@RestController`, `@RequestMapping("/api/auth")`, `@RequiredArgsConstructor`, `@Slf4j`.

```java
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
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        log.info("Register request: username={}", request.getUsername());
        try {
            UserResponse response = authService.register(request);
            return ResponseEntity.ok(ApiResponse.success("User registered successfully", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ================================================================
    // POST /api/auth/login
    // ================================================================

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        log.info("Login request: username={}", request.getUsername());
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ================================================================
    // POST /api/auth/refresh
    // ================================================================

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        try {
            LoginResponse response = authService.refresh(request);
            return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(e.getMessage()));
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
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(pem);
    }

    // ================================================================
    // Global exception handler for validation errors
    // ================================================================

    // Use @ExceptionHandler for MethodArgumentNotValidException if needed.
    // The common-web module already provides a global exception handler —
    // verify it handles validation errors. If not, add:
    //
    // @ExceptionHandler(MethodArgumentNotValidException.class)
    // public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    //     String message = e.getBindingResult().getFieldErrors().stream()
    //             .map(f -> f.getField() + ": " + f.getDefaultMessage())
    //             .collect(java.util.stream.Collectors.joining(", "));
    //     return ResponseEntity.badRequest().body(ApiResponse.error(message));
    // }
}
```

Key design decisions:
- `/register` catches `IllegalArgumentException` and returns 400 (bad request)
- `/login` catches `IllegalArgumentException` and returns 401 (unauthorized) — don't distinguish between "user not found" and "wrong password" for security
- `/refresh` catches `IllegalArgumentException` and returns 401
- `/validate` returns 200 OK in both valid and invalid cases (it's not an error — the response body's `valid` field tells the caller). The Gateway calls this as a fallback; it checks `data.valid`, not the HTTP status.
- `/public-key` returns plain text PEM with `Content-Type: text/plain`
- `@Valid` on request bodies triggers Jakarta Bean Validation (from spring-boot-starter-validation)

### Step 2: Verify compilation

```
./gradlew :services:auth-service:compileJava
```

---

## Acceptance Criteria

- [ ] `AuthController.java` exists with `@RestController` and `@RequestMapping("/api/auth")`
- [ ] `POST /register` — accepts `@Valid RegisterRequest`, returns 200 with `UserResponse` or 400 on error
- [ ] `POST /login` — accepts `@Valid LoginRequest`, returns 200 with `LoginResponse` or 401 on error
- [ ] `POST /refresh` — accepts `@Valid RefreshTokenRequest`, returns 200 with `LoginResponse` or 401 on error
- [ ] `POST /validate` — reads `Authorization` header, returns 200 with `ValidateResponse` (valid=true/false)
- [ ] `GET /public-key` — returns PEM string as `text/plain` (NOT wrapped in ApiResponse)
- [ ] All error responses use `ApiResponse.error(message)` with appropriate HTTP status codes
- [ ] `./gradlew :services:auth-service:compileJava` succeeds
