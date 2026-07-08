# Plan 13-03: Auth Service — Repositories

**Objective:** Create the three Spring Data JPA repository interfaces: `UserRepository`, `RoleRepository`, and `RefreshTokenRepository`.

**Depends on:** Plan 13-02 (entities must exist — repositories reference entity classes).

**Estimated files to create:** 3

---

## Files to Read First

Before writing any code, open these files to understand the patterns:

| File | Why |
|------|-----|
| `services/reference-data-service/src/main/java/.../repository/CityRepository.java` | Repository pattern: extends `JpaRepository`, `@Repository` annotation |
| `services/auth-service/src/main/java/.../entity/User.java` | Know the entity fields for query method naming |
| `services/auth-service/src/main/java/.../entity/Role.java` | Know the entity fields |
| `services/auth-service/src/main/java/.../entity/RefreshToken.java` | Know the entity fields |

---

## Steps

### Step 1: Create `UserRepository`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/repository/UserRepository.java`

```java
package com.insurancemanagementsystem.auth.repository;

import com.insurancemanagementsystem.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
```

Query methods:
- `findByUsername` — used at login to look up user by username
- `findByEmail` — used at registration to check email uniqueness
- `existsByUsername` — used at registration to check username uniqueness
- `existsByEmail` — used at registration to check email uniqueness

### Step 2: Create `RoleRepository`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/repository/RoleRepository.java`

```java
package com.insurancemanagementsystem.auth.repository;

import com.insurancemanagementsystem.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);
}
```

`findByName` is used to look up the `CUSTOMER` role when assigning it to new users at registration.

### Step 3: Create `RefreshTokenRepository`

**File:** `services/auth-service/src/main/java/com/insurancemanagementsystem/auth/repository/RefreshTokenRepository.java`

```java
package com.insurancemanagementsystem.auth.repository;

import com.insurancemanagementsystem.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    void revokeAllForUser(@Param("userId") UUID userId);
}
```

Query methods:
- `findByTokenHash` — at refresh time, look up the stored token by its SHA-256 hash
- `revokeAllForUser` — revokes all active refresh tokens for a user (used when they log out from all devices, or as a security measure). Uses `@Modifying` because it's an UPDATE, not a SELECT.

### Step 4: Verify compilation

From the repo root, run:
```
./gradlew :services:auth-service:compileJava
```

Fix any compilation errors before marking this plan complete.

---

## Acceptance Criteria

- [x] `UserRepository.java` exists with `findByUsername`, `findByEmail`, `existsByUsername`, `existsByEmail`
- [x] `RoleRepository.java` exists with `findByName`
- [x] `RefreshTokenRepository.java` exists with `findByTokenHash` and `revokeAllForUser`
- [x] All three extend `JpaRepository` with correct entity + ID types
- [x] All three have `@Repository` annotation
- [x] `./gradlew :services:auth-service:compileJava` succeeds
