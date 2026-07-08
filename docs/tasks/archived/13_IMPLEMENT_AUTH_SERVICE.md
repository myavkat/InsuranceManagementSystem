Auth Service — Requirements Map

1. Project Scaffolding

Files to create:

┌───────────────────────────────────────────────────────────────┬───────────────────────────────────────────────────────────────────┐
│                             File                              │                              Purpose                              │
├───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
│ services/auth-service/build.gradle.kts                        │ Gradle build (copy pattern from reference-data-service)           │
├───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
│ services/auth-service/src/main/resources/application.yml      │ Service config (port 8087, spring.application.name: auth-service) │
├───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┤
│ services/auth-service/src/main/resources/keys/private-key.pem │ RSA private key for signing JWTs (dev only, never commit)         │
└───────────────────────────────────────────────────────────────┴───────────────────────────────────────────────────────────────────┘

Modifications to existing files:

┌──────────────────────────────────────────┬──────────────────────────────────────────────────────────────────────────────────────┐
│                   File                   │                                        Change                                        │
├──────────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ settings.gradle.kts                      │ Uncomment include("services:auth-service")                                           │
├──────────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ infra/docker/docker-compose.services.yml │ Add auth-service container definition (port 8087, depends on auth-db, eureka-server) │
├──────────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ .env.template                            │ Already has AUTH_DB_* vars — verify they're complete                                 │
└──────────────────────────────────────────┴──────────────────────────────────────────────────────────────────────────────────────┘

Build dependencies (from reference-data-service pattern):
implementation(project(":common:common-message"))
implementation(project(":common:common-web"))
// + spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation
// + spring-cloud-stream, spring-cloud-stream-binder-kafka (no SAGA events per spec, but for consistency)
// + spring-cloud-starter-netflix-eureka-client
// + spring-boot-starter-security (for BCrypt + auth endpoints)
// + io.jsonwebtoken:jjwt-api / jjwt-impl / jjwt-jackson (JWT generation)
// + org.postgresql:postgresql
// + lombok

---
2. Database Schema

Already exists at infra/sql/auth_db/init.sql — 4 tables:

┌────────────────┬─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│     Table      │                                                       Purpose                                                       │
├────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ users
└────────────────┴─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

Seed data already included: 3 roles + 1 admin user.

---
3. Entities

User — com.insurancemanagementsystem.auth.entity.User
- id (UUID), username, email, passwordHash, enabled, accountNonLocked, failedAttempts, lockTime, createdAt, updatedAt
- @ManyToMany(fetch = EAGER) roles
- @PrePersist/@PreUpdate lifecycle callbacks for timestamps

Role — com.insurancemanagementsystem.auth.entity.Role
- id (UUID), name (ENUM-like: ADMIN, AGENT, CUSTOMER)

RefreshToken — com.insurancemanagementsystem.auth.entity.RefreshToken
- id (UUID), user (M→1), tokenHash, expiresAt, revoked

---
4. Repositories

┌─────────────────

---
5. API Endpoints

All under @RequestMapping("/api/auth"):

┌────────┬─────────────┬───────────────────────────────┬───────────────────────────────────────┬───────────────────────────────────────────────────────────────────────────────┐
│ Method │    Path     │         Request Body          │               Response                │                                     Notes                                     │
├────────┼─────────────┼───────────────────────────────┼───────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ POST   │ /register   │ { username, email, password } │ ApiResponse<UserResponse>             │ Validate unique username/email, BCrypt hash, assign CUSTOMER role             │
├────────┼─────────────┼───────────────────────────────┼───────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ POST   │ /login      │ { username, password }        │ ApiResponse<LoginResponse>            │ Verify credentials, check lockout, generate access + refresh token            │
├────────┼─────────────┼───────────────────────────────┼───────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ POST   │ /refresh    │ { refreshToken }              │ ApiResponse<LoginResponse>            │ Look up hash, verify not revoked/expired, rotate (revoke old, issue new pair) │
├────────┼─────────────┼───────────────────────────────┼───────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ POST   │ /validate   │ Authorization: Bearer <token> │ ApiResponse<{ valid, userId, roles }> │ Used by Gateway — validates JWT signature + expiry                            │
├────────┼─────────────┼───────────────────────────────┼───────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────┤
│ GET    │ /public-key │ —                             │ PEM string                            │ Returns the RSA public key for Gateway's local validation                     │
└────────┴─────────────┴───────────────────────────────┴───────────────────────────────────────┴───────────────────────────────────────────────────────────────────────────────┘

---
6. Response Types
All wrapped in the standard ApiResponse<T> envelope: { success: boolean, data?: T, message?: string, timestamp: string }

---
7. Security Rules (from outline)

┌──────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────┐
│       Rule       │                                        Implementation                                        │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ Password hashing │ BCrypt with strength 12 (BCryptPasswordEncoder)                                              │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ Access token     │ RSA-256 signed JWT, 15-minute expiry                                                         │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ Claims           │ sub (userId), roles (array), iat, exp, jti                                                   │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ Refresh token    │ Opaque UUID stored as SHA-256 hash, 7-day expiry, single-use (rotated)                       │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ Login lockout    │ 5 failed attempts → 15-minute lock (accountNonLocked + lockTime fields)                      │
├──────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────┤
│ Token validation │ Gateway validates tokens locally via public key, auth service provides /validate as fallback │
└──────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────┘

---
8. Key Architecture Decisions

- No SAGA events (per 02_MICROSERVICES_SPECIFICATIONS.md:34) — synchronous auth only. No Kafka consumers needed for core functionality.
- Public key in PEM format — the Gateway already loads it from classpath:keys/public-key.pem (JwtPublicKeyProvider.java). The auth-service signs with the matching private key. The /public-key endpoint allows the Gateway to refresh it in production.
- Service port — 8087 (already allocated in 13_ENVIRONMENT_QUIRKS.md).
- Spring Security — used for BCryptPasswordEncoder + securing the auth service's own endpoints (optional minimal config). The auth service is NOT a resource server consuming JWTs — it issues them.

---
9. Integration Points

┌────────────────────────┬────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│      Integration       │                                                   Detail                                                   │
├────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ API Gateway            │ Routes POST /api/auth/** to lb://auth-service — already configured                                         │
├────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Gateway JWT validation │ JwtAuthFilter.java loads the public key to validate tokens. Auth service must use a matching RSA keypair   │
├────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Frontend               │ frontend-next/src/lib/api/auth.ts calls login, register, validateToken — expects the response shapes above │
├────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Eureka                 │ Registers as auth-service so the Gateway can lb:// to it                                                   │
├────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Docker Compose         │ Need to add auth-service container in docker-compose.services.yml (port mapping 8087:8080)                 │
└────────────────────────┴────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

---
10. Implementation Order

1. Scaffold — build.gradle.kts, uncomment in settings.gradle.kts, application.yml
2. Entities — User, Role, RefreshToken with JPA mappings
3. Repositories — UserRepository, RoleRepository, RefreshTokenRepository
4. JWT Utility — JwtTokenProvider (sign + validate, load private key)
5. DTOs — LoginRequest, LoginResponse, RegisterRequest, UserResponse, ValidateResponse
6. Service — AuthService (register, login, refresh, validate, lockout logic)
7. Controller — AuthController (4 endpoints)
8. Public Key Endpoint — GET /api/auth/public-key
9. Docker Compose — add service to docker-compose.services.yml
10. Test — verify POST /api/auth/login returns a valid JWT, Gateway can validate it
