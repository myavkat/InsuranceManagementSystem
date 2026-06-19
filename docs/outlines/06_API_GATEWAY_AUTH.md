# API Gateway & Auth Outline

## API Gateway (Spring Cloud Gateway)

**Purpose:** Single entry point for all external requests. Routes traffic to the appropriate microservice, handles authentication validation, rate limiting, and request/response transformation.

---

### Route Configuration

| Route Path | Target Service | Auth Required |
|------------|---------------|---------------|
| `/api/auth/**` | Auth Service | No (except `/validate`) |
| `/api/customers/**` | Customer Service | Yes |
| `/api/vehicles/**` | Vehicle Service | Yes |
| `/api/real-estate/**` | RealEstate Service | Yes |
| `/api/insurances/**` | Insurance Service | Yes |
| `/api/estimations/**` | Estimation Service | Yes |
| `/api/reference-data/**` | Reference Data Service | No (or minimal) |

### Gateway Filter Chain

1. **Rate Limiter** — token bucket per client IP or user ID
2. **JWT Authentication Filter** — extracts JWT from `Authorization: Bearer <token>`, validates with Auth Service (or local public key), injects user context as headers (`X-User-Id`, `X-User-Roles`)
3. **Route Filter** — forwards request to target service with modified path
4. **Response Filter** — standardizes error responses, adds CORS headers

### Rate Limiting

- Default: 100 requests/minute per user, 1000/minute per IP.
- Configurable per route (e.g., `/api/auth/login` stricter at 10/minute per IP).
- Redis-backed token bucket implementation.

---

## Auth Service

**Purpose:** Dedicated service for user management and JWT lifecycle.

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/register` | Create new user (username, email, password) → returns user |
| POST | `/api/auth/login` | Authenticate → returns `{ accessToken, refreshToken, expiresIn }` |
| POST | `/api/auth/refresh` | Valid refresh token → returns new access token |
| POST | `/api/auth/validate` | Validates JWT → returns `{ valid, userId, roles }` (used by Gateway) |

### Token Format

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "dGhpcyBpcyBh...",
  "expiresIn": 900,
  "tokenType": "Bearer"
}
```

- **Access Token:** JWT, signed with RSA-256, 15-minute expiry.
- **Refresh Token:** Opaque UUID stored in DB, 7-day expiry, single-use (rotated on refresh).
- **Claims:** `sub` (userId), `roles` (array), `iat`, `exp`, `jti`.

### Security Rules

- Passwords hashed with BCrypt (strength 12).
- Refresh tokens stored as SHA-256 hash in database.
- Failed login attempts: lock account after 5 failures for 15 minutes.
- JWT public key exposed via Gateway for local validation (reduces Auth Service calls).
