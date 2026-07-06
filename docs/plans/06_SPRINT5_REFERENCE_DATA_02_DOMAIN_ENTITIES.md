# Plan: Sprint 5 — Domain Entities, Repositories & DTOs

## Context Files (Read Before Starting)

| File | Purpose |
|------|---------|
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | §7 Reference Data Service — entities City, Profession |
| `docs/outlines/10_JAVA_CONVENTIONS.md` | Instant vs LocalDate, Lombok order, Jackson 3 annotations |
| `infra/sql/reference_data_db/init.sql` | DB schema: `cities(id INT, name, plate_code)`, `professions(id INT, name)`, seed data |
| `services/reference-skeleton/src/main/java/.../entity/SampleEntity.java` | Entity pattern: `@PrePersist`/`@PreUpdate`, UUID PK, Instant timestamps |
| `services/reference-skeleton/src/main/java/.../dto/ApiResponse.java` | ApiResponse envelope — MUST copy this pattern |
| `services/reference-skeleton/src/main/java/.../repository/SampleRepository.java` | Repository pattern: `JpaRepository` |
| `services/customer-service/src/main/java/.../entity/Customer.java` | Real service entity example |

## Prerequisites

- [x] Plan 01 (Service Scaffold) completed — project compiles

## Conventions to Apply

- **Lombok order:** `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` then JPA annotations
- **Timestamps:** `createdAt` + `updatedAt` use `java.time.Instant`, managed by `@PrePersist` / `@PreUpdate`
- **No UUID PKs here:** The SQL init script uses `INT` primary keys (matching plate codes for cities). Entities use `Integer` IDs.
- **Jackson 3:** `@JsonInclude(JsonInclude.Include.NON_NULL)` on ApiResponse; annotations are `com.fasterxml.jackson.annotation.*`
- **DTOs** use `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` — no JPA annotations
- **ApiResponse** uses `@JsonInclude(JsonInclude.Include.NON_NULL)` for null-safety

## Implementation Steps

### Step 1: Create `City` Entity

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/entity/City.java`

Package: `com.insurancemanagementsystem.referencedata.entity`

```java
@Entity
@Table(name = "cities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class City {

    @Id
    @Column(name = "id")
    private Integer id;          // INT, matches plate code — NOT generated

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "plate_code", nullable = false, length = 2)
    private String plateCode;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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

**Key decisions:**
- `Integer` ID (not UUID) — matches `init.sql` which uses `INT` PKs. The city ID is its plate number.
- `@GeneratedValue` is NOT used — IDs are pre-assigned from seed data.
- `plateCode` is `VARCHAR(2)` — e.g., "01", "34", "06"
- `createdAt` / `updatedAt` follow the Instant convention from [10_JAVA_CONVENTIONS.md](../../outlines/10_JAVA_CONVENTIONS.md)

### Step 2: Create `Profession` Entity

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/entity/Profession.java`

Package: `com.insurancemanagementsystem.referencedata.entity`

```java
@Entity
@Table(name = "professions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profession {

    @Id
    @Column(name = "id")
    private Integer id;          // INT — NOT generated

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

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

### Step 3: Create `CityRepository`

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/repository/CityRepository.java`

Package: `com.insurancemanagementsystem.referencedata.repository`

```java
@Repository
public interface CityRepository extends JpaRepository<City, Integer> {
    List<City> findAllByOrderByNameAsc();
}
```

- Extends `JpaRepository<City, Integer>` — entity type City, ID type Integer
- `findAllByOrderByNameAsc()` — returns cities sorted alphabetically by name

### Step 4: Create `ProfessionRepository`

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/repository/ProfessionRepository.java`

Package: `com.insurancemanagementsystem.referencedata.repository`

```java
@Repository
public interface ProfessionRepository extends JpaRepository<Profession, Integer> {
    List<Profession> findAllByOrderByNameAsc();
}
```

### Step 5: Create `CityResponse` DTO

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/dto/CityResponse.java`

Package: `com.insurancemanagementsystem.referencedata.dto`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityResponse {
    private Integer id;
    private String name;
    private String plateCode;
}
```

**Mapping strategy:** Service layer maps `City` entity → `CityResponse` DTO. Never expose entities directly in API responses — use DTOs.

### Step 6: Create `ProfessionResponse` DTO

- [x] Create `services/reference-data-service/src/main/java/com/insurancemanagementsystem/referencedata/dto/ProfessionResponse.java`

Package: `com.insurancemanagementsystem.referencedata.dto`

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionResponse {
    private Integer id;
    private String name;
}
```

### Step 7: Create `ApiResponse<T>` Envelope

- [x] (Skipped — shared ApiResponse exists in common/common-web/ and is already a dependency)

Package: `com.insurancemanagementsystem.referencedata.dto`

**Pattern:** Copy DIRECTLY from `services/reference-skeleton/src/main/java/.../dto/ApiResponse.java`:
- `@JsonInclude(JsonInclude.Include.NON_NULL)`
- Fields: `boolean success`, `String message`, `T data`, `Instant timestamp`
- Static factories: `success(T data)`, `success(String message, T data)`, `error(String message)`
- Builder pattern via `@Builder`

**Do NOT use the common-web ApiResponse if one exists** — check `common/common-web/src/` first. If a shared `ApiResponse` exists in common-web, use that instead of duplicating.

### Step 8: Verify Compilation

- [x] Run: `.\gradlew.bat :services:reference-data-service:compileJava`
- [x] All entity mappings match the SQL schema (`cities` table, `professions` table)
- [x] No JPA mapping errors (column names must match exactly: `plate_code`, `created_at`, `updated_at`)

## Deliverables (this plan)

- [x] `City.java` — entity mapped to `cities` table
- [x] `Profession.java` — entity mapped to `professions` table
- [x] `CityRepository.java` — JPA repository with sorted query
- [x] `ProfessionRepository.java` — JPA repository with sorted query
- [x] `CityResponse.java` — DTO for city API responses
- [x] `ProfessionResponse.java` — DTO for profession API responses
- [x] `ApiResponse.java` (shared from common-web) — standard response envelope
- [x] `.\gradlew.bat :services:reference-data-service:compileJava` passes
