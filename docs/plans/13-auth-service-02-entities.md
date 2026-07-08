# Plan 13-02: Auth Service — JPA Entities

**Objective:** Create the three JPA entity classes: `User`, `Role`, and `RefreshToken` with correct JPA mappings matching the existing database schema.

**Depends on:** Plan 13-01 (scaffolding must be complete — build file, application.yml, and main class must exist).

**Estimated files to create:** 3

---

## Files to Read First

Before writing any code, open these files to understand the patterns:

| File | Why |
|------|-----|
| `infra/sql/auth_db/init.sql` | The database schema these entities must map to — column names, types, constraints |
| `services/reference-data-service/src/main/java/.../entity/City.java` | Entity pattern: Lombok annotation order, `@PrePersist`/`@PreUpdate`, `Instant` timestamps |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Lombok order convention (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA), `Instant` for timestamps |
| `docs/outlines/06_API_GATEWAY_AUTH.md` | Refresh token as SHA-256 hash, security rules |

---

## Database Schema Reference (from `init.sql`)

### `users` table
| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK, auto-generated via `uuid_generate_v4()` |
| `username` | VARCHAR(50) | UNIQUE, NOT NULL |
| `email` | VARCHAR(100) | UNIQUE, NOT NULL |
| `password_hash` | VARCHAR(255) | NOT NULL |
| `enabled` | BOOLEAN | DEFAULT TRUE |
| `account_non_locked` | BOOLEAN | DEFAULT TRUE |
| `failed_attempts` | INT | DEFAULT 0 |
| `lock_time` | TIMESTAMP | nullable |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |

### `roles` table
| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK, auto-generated via `uuid_generate_v4()` |
| `name` | VARCHAR(20) | UNIQUE, NOT NULL |

### `user_roles` (join table)
| Column | Type | Constraints |
|--------|------|-------------|
| `user_id` | UUID | FK → users(id), PK |
| `role_id` | UUID | FK → roles(id), PK |

### `refresh_tokens` table
| Column | Type | Constraints |
|--------|------|-------------|
| `id` | UUID | PK, auto-generated |
| `user_id` | UUID | FK → users(id), NOT NULL |
| `token_hash` | VARCHAR(255) | NOT NULL |
| `expires_at` | TIMESTAMP | NOT NULL |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| `revoked` | BOOLEAN | DEFAULT FALSE |

---

## Steps

### Step 1: Create `User` entity

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/entity/User.java`

Follow the entity pattern from `City.java`:

```java
package com.insurancemanagementsystem.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private Boolean accountNonLocked = true;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private Integer failedAttempts = 0;

    @Column(name = "lock_time")
    private Instant lockTime;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

Key mapping details:
- `password_hash` column maps to field `passwordHash` (snake_case to camelCase is automatic in Hibernate)
- `account_non_locked` maps to `accountNonLocked`
- `failed_attempts` maps to `failedAttempts`
- `lock_time` maps to `lockTime`
- `created_at` / `updated_at` maps to `createdAt` / `updatedAt`
- `@ManyToMany(fetch = EAGER)` on roles — EAGER is intentional for auth (always needed for security context)
- `@Builder.Default` on boolean/Integer defaults to preserve the DB defaults when building
- UUID id uses `GenerationType.UUID` which works with PostgreSQL `uuid_generate_v4()`

### Step 2: Create `Role` entity

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/entity/Role.java`

```java
package com.insurancemanagementsystem.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "roles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 20)
    private String name;
}
```

This entity has no timestamps (the `roles` table has no `created_at`/`updated_at` columns — see init.sql).

### Step 3: Create `RefreshToken` entity

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/entity/RefreshToken.java`

```java
package com.insurancemanagementsystem.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
```

Key mapping details:
- `@ManyToOne(fetch = LAZY)` to User — LAZY because we always have userId separately
- `token_hash` maps to `tokenHash`
- `expires_at` maps to `expiresAt`
- `revoked` defaults to false via `@Builder.Default`
- No `@PreUpdate` — the table has no `updated_at` column

### Step 4: Verify compilation

From the repo root, run:
```
./gradlew :services:auth-service:compileJava
```

Fix any compilation errors before marking this plan complete.

---

## Acceptance Criteria

- [x] `User.java` exists with all columns mapped correctly to the `users` table
- [x] `Role.java` exists with `name` column mapped
- [x] `RefreshToken.java` exists with `@ManyToOne` to User and all columns mapped
- [x] All three entities use `Instant` for timestamp fields
- [x] All three entities follow the Lombok annotation order: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, then JPA
- [x] All three entities use `GenerationType.UUID` for ID generation
- [x] `./gradlew :services:auth-service:compileJava` succeeds
