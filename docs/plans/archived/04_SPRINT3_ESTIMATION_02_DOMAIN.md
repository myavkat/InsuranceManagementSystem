# Plan: Sprint 3 — Estimation Service — Step 2: Domain Layer

## Objective
Create the JPA entity (`Estimation`), the `SagaEvent` deduplication entity, and both JPA repositories.

## Context Files to Read First
1. **`services/customer-service/src/main/java/.../entity/Customer.java`** — Entity pattern (Lombok order, @PrePersist/@PreUpdate, `GenerationType.UUID`, `Instant` timestamps)
2. **`services/customer-service/src/main/java/.../repository/CustomerRepository.java`** — Repository pattern
3. **`infra/sql/estimation_db/init.sql`** — DB schema (columns, types, constraints, indexes) — entity must match this
4. **`docs/outlines/10_JAVA_CONVENTIONS.md`** — Lombok annotation order: `@Data @Builder @NoArgsConstructor @AllArgsConstructor` then JPA annotations
5. **`docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md`** — Estimation entity specification (section 6)

## Files to Create

### 1. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`

The `estimation_db/init.sql` schema is:

```sql
CREATE TABLE estimations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID UNIQUE NOT NULL,
    customer_id UUID,
    vehicle_id UUID,
    real_estate_id UUID,
    insurance_type_id INT,
    company_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'COMPLETED', 'REJECTED')),
    premium DECIMAL(12,2),
    details JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Entity rules:**
- Package: `com.insurancemanagementsystem.estimation.entity`
- Table name: `estimations`
- ID: `UUID`, `@GeneratedValue(strategy = GenerationType.UUID)` (maps to `uuid_generate_v4()` via Hibernate 6+)
- `sagaId` — `UUID`, `unique = true`, `nullable = false`
- `customerId` — `UUID`, nullable
- `vehicleId` — `UUID`, nullable
- `realEstateId` — `UUID`, nullable
- `insuranceTypeId` — `Integer`
- `companyId` — `UUID`
- `status` — `String`, length 20, nullable = false. Use an `EstimationStatus` enum (inner enum or separate file) with `STARTED`, `COMPLETED`, `REJECTED`. Store as string via `@Enumerated(EnumType.STRING)`.
- `premium` — `BigDecimal`, precision = 12, scale = 2
- `details` — `String` (JSONB stored as text). Or use `com.fasterxml.jackson.databind.JsonNode` with `@Column(columnDefinition = "JSONB")`. Use `String` for simplicity and type-safety at this stage.
- `createdAt` — `Instant`, `@Column(updatable = false)`
- `updatedAt` — `Instant`
- `@PrePersist` / `@PreUpdate` lifecycle callbacks

**Include the inner enum:**
```java
public enum Status {
    STARTED, COMPLETED, REJECTED
}
```

### 2. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/SagaEvent.java`

The saga_events table for idempotency:

```sql
CREATE TABLE saga_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

This will be used by the `SagaEventRepository` for database-backed deduplication. The task says to deduplicate by `(sagaId, eventType)`.

Create a composite unique constraint on `(saga_id, event_type)`.

Entity fields:
- `id` — `UUID`, PK
- `sagaId` — `UUID`, nullable = false
- `eventType` — `String`, length 50, nullable = false
- `receivedAt` — `Instant`

Add `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"saga_id", "event_type"}))`.

### 3. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/EstimationRepository.java`

```java
package com.insurancemanagementsystem.estimation.repository;

import com.insurancemanagementsystem.estimation.entity.Estimation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EstimationRepository extends JpaRepository<Estimation, UUID> {
    Optional<Estimation> findBySagaId(UUID sagaId);
    List<Estimation> findByCustomerId(UUID customerId);
    List<Estimation> findByStatus(Estimation.Status status);
    List<Estimation> findByStatusAndCreatedAtBefore(Estimation.Status status, Instant createdAt);
    List<Estimation> findByCustomerIdAndStatus(UUID customerId, Estimation.Status status);
    List<Estimation> findByCreatedAtBetween(Instant from, Instant to);
}
```

**Key methods:**
- `findBySagaId(UUID)` — find estimation by SAGA correlation ID
- `findByCustomerId(UUID)` — list customer's estimations
- `findByStatus(Status)` — filter by status
- `findByStatusAndCreatedAtBefore(Status, Instant)` — used by timeout scheduler (find stale STARTED)
- `findByCreatedAtBetween(Instant, Instant)` — date range filter

### 4. `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/SagaEventRepository.java`

```java
package com.insurancemanagementsystem.estimation.repository;

import com.insurancemanagementsystem.estimation.entity.SagaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaEventRepository extends JpaRepository<SagaEvent, UUID> {
    boolean existsBySagaIdAndEventType(UUID sagaId, String eventType);
    Optional<SagaEvent> findBySagaIdAndEventType(UUID sagaId, String eventType);
}
```

## Verification
- Compile: `.\gradlew.bat :services:estimation-service:compileJava`
- No test failures (no tests to run yet)
- Make sure the entity column names match the SQL schema exactly (`saga_id` → `sagaId`, `insurance_type_id` → `insuranceTypeId` etc.)

## File Listing Summary
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/SagaEvent.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/EstimationRepository.java` ✅
- `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/repository/SagaEventRepository.java` ✅
