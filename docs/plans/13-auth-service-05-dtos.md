# Plan 13-05: Auth Service — DTOs

**Objective:** Create all request and response DTO classes needed by the auth service endpoints. These must match the shapes expected by the frontend and gateway.

**Depends on:** Plan 13-01 (build file and common-web dependency must exist). Does NOT depend on entities or repositories.

**Estimated files to create:** 6

---

## Files to Read First

Before writing any code, open these files:

| File | Why |
|------|-----|
| `frontend-next/src/lib/api/auth.ts` | Frontend expects these exact response shapes: `LoginResponse` (accessToken, refreshToken, expiresIn, tokenType), `UserResponse` (userId, username, email, roles), `RegisterRequest` (username, email, password), `LoginRequest` (username, password) |
| `services/reference-data-service/src/main/java/.../dto/CityResponse.java` | DTO pattern: Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` |
| `common/common-web/src/main/java/.../dto/ApiResponse.java` | The response envelope used by all controllers |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Lombok annotation ordering |
| `docs/outlines/06_API_GATEWAY_AUTH.md` | Token format: `{ accessToken, refreshToken, expiresIn, tokenType }` |

---

## Frontend Contract (from `auth.ts`)

These interfaces define the exact JSON shapes the frontend expects:

```typescript
// Request bodies:
LoginRequest:    { username: string; password: string }
RegisterRequest: { username: string; email: string; password: string }

// Response bodies (wrapped in ApiResponse<T>):
LoginResponse:   { accessToken: string; refreshToken: string; expiresIn: number; tokenType: string }
UserResponse:    { userId: string; username: string; email: string; roles: string[] }
ValidateResponse: { valid: boolean; userId: string; roles: string[] }
```

The refresh endpoint receives `{ refreshToken: string }` and returns a `LoginResponse`.

---

## Steps

### Step 1: Create `LoginRequest`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/dto/LoginRequest.java`

```java
package com.insurancemanagementsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
```

### Step 2: Create `RegisterRequest`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/dto/RegisterRequest.java`

```java
package com.insurancemanagementsystem.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;
}
```

### Step 3: Create `RefreshTokenRequest`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/dto/RefreshTokenRequest.java`

```java
package com.insurancemanagementsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
```

### Step 4: Create `LoginResponse`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/dto/LoginResponse.java`

Must match the TypeScript `LoginResponse` interface: `accessToken`, `refreshToken`, `expiresIn`, `tokenType`.

```java
package com.insurancemanagementsystem.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;       // seconds (not milliseconds — frontend expects seconds)
    private String tokenType;     // "Bearer"
}
```

Note: `expiresIn` is in **seconds** (matching the frontend/standard OAuth convention). The JwtTokenProvider returns milliseconds — the service layer must convert to seconds.

### Step 5: Create `UserResponse`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/dto/UserResponse.java`

Must match the TypeScript `UserResponse` interface: `userId`, `username`, `email`, `roles`.

```java
package com.insurancemanagementsystem.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String userId;
    private String username;
    private String email;
    private List<String> roles;
}
```

### Step 6: Create `ValidateResponse`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/dto/ValidateResponse.java`

Must match the TypeScript validateToken response: `valid`, `userId`, `roles`.

```java
package com.insurancemanagementsystem.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateResponse {

    private boolean valid;
    private String userId;
    private List<String> roles;
}
```

### Step 7: Verify compilation

```
./gradlew :services:auth-service:compileJava
```

---

## Acceptance Criteria

- [ ] `LoginRequest.java` — username, password with `@NotBlank` validation
- [ ] `RegisterRequest.java` — username, email, password with `@NotBlank`, `@Email`, `@Size` validation
- [ ] `RefreshTokenRequest.java` — refreshToken with `@NotBlank` validation
- [ ] `LoginResponse.java` — accessToken, refreshToken, expiresIn (seconds), tokenType ("Bearer")
- [ ] `UserResponse.java` — userId, username, email, roles
- [ ] `ValidateResponse.java` — valid, userId, roles
- [ ] All DTOs use Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- [ ] Property names match the frontend's TypeScript interfaces exactly (camelCase)
- [ ] `./gradlew :services:auth-service:compileJava` succeeds
