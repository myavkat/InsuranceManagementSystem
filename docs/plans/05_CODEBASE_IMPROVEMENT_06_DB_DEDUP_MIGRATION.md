# Plan: Fix 11 — Migrate Customer Service & Insurance Service to DB-Backed SagaEvent Dedup

## Objective

Replace the in-memory `DeduplicationStore` in **customer-service** and **insurance-service** with durable, DB-backed deduplication using the `saga_events` table pattern (matching what estimation-service already does). This eliminates the crash-safety and horizontal-scaling gaps of in-memory dedup.

## Why

The in-memory `DeduplicationStore` (ConcurrentHashMap with TTL) currently used by customer-service and insurance-service has two fundamental problems:

1. **Crash loss:** On service restart, all dedup state is lost — duplicate events that arrived before the restart will be re-processed
2. **No horizontal scaling:** Multiple instances of the same service each have their own in-memory store. The same event can be processed by different instances

The `SagaEvent` table with `UNIQUE(saga_id, event_type)` constraint — already proven in estimation-service — solves both problems atomically at the database level.

## Current State

| Aspect | estimation-service | customer-service | insurance-service |
|--------|-------------------|-----------------|-------------------|
| **Dedup mechanism** | DB-backed `SagaEvent` table | In-memory `DeduplicationStore` | In-memory `DeduplicationStore` |
| **Dedup class** | `isDuplicateSagaEvent()` method in consumer | `DeduplicationStore.java` (60 lines) | `DeduplicationStore.java` (60 lines) |
| **SagaEvent entity** | ✅ `entity/SagaEvent.java` | ❌ Not exists | ❌ Not exists |
| **SagaEventRepository** | ✅ `repository/SagaEventRepository.java` | ❌ Not exists | ❌ Not exists |
| **saga_events table** | ✅ `estimation_db` | ❌ `customer_db` missing | ❌ `insurance_db` missing |

## Approach

For each service (customer-service, insurance-service):
1. Create `SagaEvent.java` entity (copy from estimation-service pattern, adjust package)
2. Create `SagaEventRepository.java` (copy from estimation-service pattern)
3. Add `saga_events` table to the service's DB init script
4. Replace `DeduplicationStore` dependency in the SAGA consumer with `SagaEventRepository`
5. Add `isDuplicateSagaEvent()` helper method to the consumer
6. Delete `DeduplicationStore.java` source file
7. Delete `DeduplicationStoreTest.java` test file (also delete `SagaAggregationStoreTest.java` in insurance if exists)
8. Update consumer tests to mock `SagaEventRepository` instead of `DeduplicationStore`

**Note on insurance-service:** It also has `SagaAggregationStore` (another in-memory store) — this is a different concern (correlating events, not dedup). Leave it unchanged.

## Context Files to Read First

### Reference — estimation-service implementation (the template to follow)
1. **`services/estimation-service/src/main/java/.../estimation/entity/SagaEvent.java`** — Entity structure, builder, unique constraint, `@PrePersist`
2. **`services/estimation-service/src/main/java/.../estimation/repository/SagaEventRepository.java`** — Repository with `existsBySagaIdAndEventType()` and `findBySagaIdAndEventType()`
3. **`services/estimation-service/src/main/java/.../estimation/config/EstimationSagaConsumer.java`** — `isDuplicateSagaEvent()` method (lines 38-50) — the try-catch dedup pattern
4. **`services/estimation-service/src/test/java/.../estimation/config/EstimationSagaConsumerTest.java`** — Test pattern for DB dedup, `ArgumentCaptor<SagaEvent>`, `DataIntegrityViolationException` mock

### Customer-service files to modify
5. **`services/customer-service/src/main/java/.../customer/config/CustomerSagaConsumer.java`** — Current consumer with `DeduplicationStore` dependency
6. **`services/customer-service/src/main/java/.../customer/config/DeduplicationStore.java`** — To be deleted
7. **`services/customer-service/src/main/java/.../customer/CustomerServiceApplication.java`** — Application main class (verify `scanBasePackages`)
8. **`infra/sql/customer_db/init.sql`** — Current schema, add `saga_events` table

### Customer-service test files to modify
9. **`services/customer-service/src/test/java/.../customer/saga/CustomerSagaConsumerTest.java`** — Current tests with `DeduplicationStore` mock
10. **glob:** `services/customer-service/src/test/**/DeduplicationStore*` — Find tests to delete

### Insurance-service files to modify
11. **`services/insurance-service/src/main/java/.../insurance/config/InsuranceSagaConsumer.java`** — Current consumer with `DeduplicationStore` dependency
12. **`services/insurance-service/src/main/java/.../insurance/config/DeduplicationStore.java`** — To be deleted
13. **`services/insurance-service/src/main/java/.../insurance/InsuranceServiceApplication.java`** — Application main class
14. **`infra/sql/insurance_db/init.sql`** — Current schema, add `saga_events` table

### Insurance-service test files to modify
15. **`services/insurance-service/src/test/java/.../insurance/saga/InsuranceSagaConsumerTest.java`** — Current tests with `DeduplicationStore` mock
16. **glob:** `services/insurance-service/src/test/**/DeduplicationStore*` — Find tests to delete

## Files to Create

### 1. `services/customer-service/src/main/java/.../customer/entity/SagaEvent.java`

Copy from estimation-service's SagaEvent.java. Only change: package name.

```java
package com.insurancemanagementsystem.customer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_events", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"saga_id", "event_type"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "received_at")
    private Instant receivedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
```

### 2. `services/customer-service/src/main/java/.../customer/repository/SagaEventRepository.java`

```java
package com.insurancemanagementsystem.customer.repository;

import com.insurancemanagementsystem.customer.entity.SagaEvent;
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

### 3. `services/insurance-service/src/main/java/.../insurance/entity/SagaEvent.java`

Same as above, but package: `com.insurancemanagementsystem.insurance.entity`

### 4. `services/insurance-service/src/main/java/.../insurance/repository/SagaEventRepository.java`

Same as above, but package: `com.insurancemanagementsystem.insurance.repository`

## Files to Modify

### Step 1: Add `saga_events` table to `infra/sql/customer_db/init.sql`

Add after the existing customers table:

```sql
CREATE TABLE IF NOT EXISTS saga_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(saga_id, event_type)
);
```

### Step 2: Add `saga_events` table to `infra/sql/insurance_db/init.sql`

Add after the last table (insurances):

```sql
CREATE TABLE IF NOT EXISTS saga_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(saga_id, event_type)
);
```

### Step 3: Modify `CustomerSagaConsumer.java`

**Replace `DeduplicationStore` dependency with `SagaEventRepository`:**

**BEFORE:**
```java
private final CustomerRepository customerRepository;
private final MessagePublisher messagePublisher;
private final DeduplicationStore deduplicationStore;
```

**AFTER:**
```java
import com.insurancemanagementsystem.customer.entity.SagaEvent;
import com.insurancemanagementsystem.customer.repository.SagaEventRepository;
import org.springframework.dao.DataIntegrityViolationException;

// Replace in constructor:
private final CustomerRepository customerRepository;
private final MessagePublisher messagePublisher;
private final SagaEventRepository sagaEventRepository;
```

**Add the `isDuplicateSagaEvent()` helper method:**
```java
private boolean isDuplicateSagaEvent(UUID sagaId, String eventType) {
    SagaEvent dedup = SagaEvent.builder()
            .sagaId(sagaId)
            .eventType(eventType)
            .build();
    try {
        sagaEventRepository.save(dedup);
        return false;
    } catch (DataIntegrityViolationException e) {
        log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
        return true;
    }
}
```

**Replace all `deduplicationStore.isDuplicate()`/`markProcessed()` calls in the consumer:**

In `handleEstimationRequested()`:
```java
// BEFORE:
String sagaIdStr = sagaId.toString();
if (deduplicationStore.isDuplicate(sagaIdStr, eventType)) {
    log.info("Duplicate event detected: sagaId={}, eventType={} — skipping", sagaId, eventType);
    return;
}
deduplicationStore.markProcessed(sagaIdStr, eventType);

// AFTER:
if (isDuplicateSagaEvent(sagaId, eventType)) {
    return;
}
```

In `handleEstimationFailed()` (after Fix 09 is applied):
```java
// BEFORE (after Fix 09):
if (deduplicationStore.isDuplicate(sagaIdStr, eventType)) {
    log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
    return;
}
deduplicationStore.markProcessed(sagaIdStr, eventType);

// AFTER:
if (isDuplicateSagaEvent(sagaId, eventType)) {
    return;
}
```

### Step 4: Modify `InsuranceSagaConsumer.java`

Same pattern as Step 3 — replace `DeduplicationStore` with `SagaEventRepository` and add `isDuplicateSagaEvent()`.

Replace all occurrences in:
- `handleEstimationRequested()` — lines ~80-84
- `handleCustomerValidated()` — lines ~99-102
- `handleVehicleValidated()` — lines ~117-120
- `handleEstimationFailed()` — after Fix 09

Each replacement follows the same pattern:
```java
// BEFORE:
if (deduplicationStore.isDuplicate(sagaId.toString(), eventType)) {
    log.info("Duplicate event: sagaId={}, eventType={} — skipping", sagaId, eventType);
    return;
}
deduplicationStore.markProcessed(sagaId.toString(), eventType);

// AFTER:
if (isDuplicateSagaEvent(sagaId, eventType)) {
    return;
}
```

### Step 5: Delete `DeduplicationStore.java` from both services

Delete these files:
- `services/customer-service/src/main/java/.../customer/config/DeduplicationStore.java`
- `services/insurance-service/src/main/java/.../insurance/config/DeduplicationStore.java`

### Step 6: Delete `DeduplicationStoreTest.java` from both services

Find and delete:
- `services/customer-service/src/test/**/DeduplicationStoreTest.java`
- `services/insurance-service/src/test/**/DeduplicationStoreTest.java`

### Step 7: Remove `DeduplicationStore` import from test files

In `CustomerSagaConsumerTest.java` and `InsuranceSagaConsumerTest.java`:
- Remove: `import ...DeduplicationStore;`
- Add: `import ...SagaEventRepository;` and `import ...SagaEvent;` and `import org.springframework.dao.DataIntegrityViolationException;`

### Step 8: Update test mocks in `CustomerSagaConsumerTest.java`

```java
// BEFORE:
@Mock
private DeduplicationStore deduplicationStore;

// AFTER:
@Mock
private SagaEventRepository sagaEventRepository;
```

Update test setup:
```java
// BEFORE (when not duplicate):
when(deduplicationStore.isDuplicate(anyString(), anyString())).thenReturn(false);
// verify:
verify(deduplicationStore).markProcessed(sagaId.toString(), eventType);

// AFTER (when not duplicate):
when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(any());
// verify:
verify(sagaEventRepository).save(any(SagaEvent.class));
```

For duplicate tests:
```java
// BEFORE:
when(deduplicationStore.isDuplicate(anyString(), anyString())).thenReturn(true);

// AFTER:
when(sagaEventRepository.save(any(SagaEvent.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));
```

Add `@Captor` for argument verification:
```java
@Captor
private ArgumentCaptor<SagaEvent> sagaEventCaptor;

// In tests:
verify(sagaEventRepository).save(sagaEventCaptor.capture());
SagaEvent saved = sagaEventCaptor.getValue();
assertThat(saved.getSagaId()).isEqualTo(sagaId);
assertThat(saved.getEventType()).isEqualTo(EventConstants.ESTIMATION_REQUESTED);
```

### Step 9: Same as Step 8 for `InsuranceSagaConsumerTest.java`

Apply identical changes.

## Verification

```bash
# 1. Compile customer-service
.\gradlew.bat :services:customer-service:compileJava
.\gradlew.bat :services:customer-service:compileTestJava

# 2. Run customer-service tests
.\gradlew.bat :services:customer-service:test

# 3. Compile insurance-service
.\gradlew.bat :services:insurance-service:compileJava
.\gradlew.bat :services:insurance-service:compileTestJava

# 4. Run insurance-service tests
.\gradlew.bat :services:insurance-service:test

# 5. Run estimation-service tests (ensure not broken by shared changes)
.\gradlew.bat :services:estimation-service:test
```

## Execution Checklist

- [x] Read reference files (estimation-service SagaEvent entity/repository/consumer)
- [x] Create `SagaEvent.java` in customer-service
- [x] Create `SagaEventRepository.java` in customer-service
- [x] Create `SagaEvent.java` in insurance-service
- [x] Create `SagaEventRepository.java` in insurance-service
- [x] Edit `infra/sql/customer_db/init.sql` — add `saga_events` table
- [x] Edit `infra/sql/insurance_db/init.sql` — add `saga_events` table
- [x] Modify `CustomerSagaConsumer.java` — replace DeduplicationStore with `isDuplicateSagaEvent()`
- [x] Modify `InsuranceSagaConsumer.java` — replace DeduplicationStore with `isDuplicateSagaEvent()`
- [x] Delete `DeduplicationStore.java` from customer-service
- [x] Delete `DeduplicationStore.java` from insurance-service
- [x] ~~Delete `DeduplicationStoreTest.java` from customer-service~~ (no such file existed)
- [x] ~~Delete `DeduplicationStoreTest.java` from insurance-service~~ (no such file existed)
- [x] ~~Update `CustomerSagaConsumerTest.java` — replace mocks~~ (integration test, no mocks to replace)
- [x] ~~Update `InsuranceSagaConsumerTest.java` — replace mocks~~ (integration test, no mocks to replace)
- [x] Compile customer-service: `BUILD SUCCESSFUL`
- [x] Customer-service tests: ALL PASS
- [x] Compile insurance-service: `BUILD SUCCESSFUL` (second run after Docker contention)
- [x] Insurance-service tests: ALL PASS
- [x] Estimation-service tests: ALL PASS (no regression)

## Risk Assessment

- **Risk:** LOW. The pattern is already proven in estimation-service. The `isDuplicateSagaEvent()` method is identical — only the package changes. The underlying `DataIntegrityViolationException` + unique constraint approach is the industry-standard dedup strategy for event-driven systems.
- **Rollback plan:** If the DB-backed approach causes issues, `DeduplicationStore.java` can be restored (both files are identical to the ones being deleted). However, this would revert crash-safety and horizontal-scaling guarantees.
- **DB migration:** The `saga_events` table addition to existing databases requires a migration. If the databases are already running, a Flyway/Liquibase migration script would be needed for the `ALTER TABLE ADD ...` equivalent. For this project (dev environments), re-running `init.sql` is sufficient.
