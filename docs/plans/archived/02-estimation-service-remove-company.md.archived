# 02 — Remove Multi-Company Concept: estimation-service

## Status: COMPLETED

## Objective

Remove the `companyId` field from the `Estimation` entity, DTOs, and service in estimation-service. Update the SQL init script. This plan assumes **Part A of plan 01 has already been completed** — the `EstimationRequestedEvent` and `PremiumCalculatedEvent` classes in `common-message` no longer have a `companyId` field.

## Prerequisites

- Read `docs/outlines/01_SYSTEM_ARCHITECTURE.md` for tech stack
- Read `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` for service specs
- Plan `01-insurance-service-remove-company.md` must be fully implemented first (specifically Part A: common-message event changes)
- Verify common-message compiles: `./gradlew :common-message:compileJava`

## What estimation-service Does NOT Have

Unlike insurance-service, estimation-service:
- Has **no** `InsuranceCompany` entity, repository, or DTOs
- Has **no** `/companies` REST endpoints
- Has **no** company validation logic
- Has **no** direct REST calls to insurance-service (communication is purely event-driven via Kafka)
- The `companyId` on `Estimation` entity is a **plain UUID column** with no FK constraint — it is simply a denormalized copy of what the client sent in the request

This makes the estimation-service changes mechanical: remove the field from entity, DTOs, service, and SQL.

---

## Part A: Entity — Estimation.java

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/entity/Estimation.java`

Remove the `companyId` field (lines 55–56).

**Current code (relevant excerpt):**
```java
@Column(name = "insurance_type_id")
private Integer insuranceTypeId;

@Column(name = "company_id")       // <-- DELETE
private UUID companyId;            // <-- DELETE

@Column(name = "trace_id")
private UUID traceId;
```

**Target state — the field block becomes:**
```java
@Column(name = "insurance_type_id")
private Integer insuranceTypeId;

@Column(name = "trace_id")
private UUID traceId;
```

No other changes to this file.

---

## Part B: DTO — EstimationRequest.java

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationRequest.java`

Remove the `companyId` field and its `@NotNull` validation. Remove the unused `UUID` import if nothing else uses it (check: `customerId` still uses it, so keep it).

**Current code:**
```java
package com.insurancemanagementsystem.estimation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequest {
    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    private UUID vehicleId;      // optional — null for non-vehicle insurances
    private UUID realEstateId;   // optional — null for non-real-estate insurances

    @NotNull(message = "Insurance type ID is required")
    private Integer insuranceTypeId;

    @NotNull(message = "Company ID is required")   // <-- DELETE
    private UUID companyId;                         // <-- DELETE
}
```

**Target state:**
```java
package com.insurancemanagementsystem.estimation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequest {
    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    private UUID vehicleId;      // optional — null for non-vehicle insurances
    private UUID realEstateId;   // optional — null for non-real-estate insurances

    @NotNull(message = "Insurance type ID is required")
    private Integer insuranceTypeId;
}
```

This is a **breaking API change**: the `POST /api/estimations` endpoint will no longer accept or require `companyId` in the request body. Frontend and API Gateway must be updated to stop sending it.

**Before request payload:**
```json
{
  "customerId": "uuid",
  "vehicleId": "uuid",
  "insuranceTypeId": 1,
  "companyId": "uuid"
}
```

**After request payload:**
```json
{
  "customerId": "uuid",
  "vehicleId": "uuid",
  "insuranceTypeId": 1
}
```

---

## Part C: DTO — EstimationResponse.java

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/dto/EstimationResponse.java`

Remove the `companyId` field and its mapping in `fromEntity()`.

**Current code:**
```java
package com.insurancemanagementsystem.estimation.dto;

import com.insurancemanagementsystem.estimation.entity.Estimation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimationResponse {
    private UUID id;
    private UUID sagaId;
    private UUID customerId;
    private UUID vehicleId;
    private UUID realEstateId;
    private Integer insuranceTypeId;
    private UUID companyId;                       // <-- DELETE
    private String status;
    private BigDecimal premium;
    private String details;
    private Instant createdAt;
    private Instant updatedAt;

    public static EstimationResponse fromEntity(Estimation estimation) {
        return EstimationResponse.builder()
                .id(estimation.getId())
                .sagaId(estimation.getSagaId())
                .customerId(estimation.getCustomerId())
                .vehicleId(estimation.getVehicleId())
                .realEstateId(estimation.getRealEstateId())
                .insuranceTypeId(estimation.getInsuranceTypeId())
                .companyId(estimation.getCompanyId())    // <-- DELETE
                .status(estimation.getStatus().name())
                .premium(estimation.getPremium())
                .details(estimation.getDetails())
                .createdAt(estimation.getCreatedAt())
                .updatedAt(estimation.getUpdatedAt())
                .build();
    }
}
```

**Target state:**
```java
package com.insurancemanagementsystem.estimation.dto;

import com.insurancemanagementsystem.estimation.entity.Estimation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimationResponse {
    private UUID id;
    private UUID sagaId;
    private UUID customerId;
    private UUID vehicleId;
    private UUID realEstateId;
    private Integer insuranceTypeId;
    private String status;
    private BigDecimal premium;
    private String details;
    private Instant createdAt;
    private Instant updatedAt;

    public static EstimationResponse fromEntity(Estimation estimation) {
        return EstimationResponse.builder()
                .id(estimation.getId())
                .sagaId(estimation.getSagaId())
                .customerId(estimation.getCustomerId())
                .vehicleId(estimation.getVehicleId())
                .realEstateId(estimation.getRealEstateId())
                .insuranceTypeId(estimation.getInsuranceTypeId())
                .status(estimation.getStatus().name())
                .premium(estimation.getPremium())
                .details(estimation.getDetails())
                .createdAt(estimation.getCreatedAt())
                .updatedAt(estimation.getUpdatedAt())
                .build();
    }
}
```

---

## Part D: Service — EstimationService.java

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/service/EstimationService.java`

Two changes in the `create()` method:
1. Remove `.companyId(request.getCompanyId())` from the `Estimation` entity builder (line 70)
2. Remove `.companyId(request.getCompanyId())` from the `EstimationRequestedEvent` builder (line 84)

**Current `create()` method (lines 55–90):**
```java
@Transactional
public EstimationResponse create(EstimationRequest request) {
    if (request.getVehicleId() == null && request.getRealEstateId() == null) {
        throw new IllegalArgumentException("Either vehicleId or realEstateId must be provided");
    }

    UUID sagaId = CorrelationIdGenerator.generateSagaId();
    UUID traceId = CorrelationIdGenerator.generateTraceId();

    Estimation estimation = Estimation.builder()
            .sagaId(sagaId)
            .customerId(request.getCustomerId())
            .vehicleId(request.getVehicleId())
            .realEstateId(request.getRealEstateId())
            .insuranceTypeId(request.getInsuranceTypeId())
            .companyId(request.getCompanyId())     // <-- DELETE
            .traceId(traceId)
            .status(Estimation.Status.STARTED)
            .build();

    estimation = estimationRepository.save(estimation);
    log.info("Created estimation id={} with sagaId={}, traceId={}", estimation.getId(), sagaId, traceId);

    EstimationRequestedEvent event = EstimationRequestedEvent.builder()
            .customerId(request.getCustomerId())
            .vehicleId(request.getVehicleId())
            .realEstateId(request.getRealEstateId())
            .insuranceTypeId(request.getInsuranceTypeId())
            .companyId(request.getCompanyId())     // <-- DELETE
            .build();

    outboxMessagePublisher.publish(event, sagaId, traceId, EventConstants.ESTIMATION_SAGA);

    return EstimationResponse.fromEntity(estimation);
}
```

**Target `create()` method:**
```java
@Transactional
public EstimationResponse create(EstimationRequest request) {
    if (request.getVehicleId() == null && request.getRealEstateId() == null) {
        throw new IllegalArgumentException("Either vehicleId or realEstateId must be provided");
    }

    UUID sagaId = CorrelationIdGenerator.generateSagaId();
    UUID traceId = CorrelationIdGenerator.generateTraceId();

    Estimation estimation = Estimation.builder()
            .sagaId(sagaId)
            .customerId(request.getCustomerId())
            .vehicleId(request.getVehicleId())
            .realEstateId(request.getRealEstateId())
            .insuranceTypeId(request.getInsuranceTypeId())
            .traceId(traceId)
            .status(Estimation.Status.STARTED)
            .build();

    estimation = estimationRepository.save(estimation);
    log.info("Created estimation id={} with sagaId={}, traceId={}", estimation.getId(), sagaId, traceId);

    EstimationRequestedEvent event = EstimationRequestedEvent.builder()
            .customerId(request.getCustomerId())
            .vehicleId(request.getVehicleId())
            .realEstateId(request.getRealEstateId())
            .insuranceTypeId(request.getInsuranceTypeId())
            .build();

    outboxMessagePublisher.publish(event, sagaId, traceId, EventConstants.ESTIMATION_SAGA);

    return EstimationResponse.fromEntity(estimation);
}
```

### Note: `EstimationSagaConsumer.java` — No Changes Needed

**File:** `services/estimation-service/src/main/java/com/insurancemanagementsystem/estimation/config/EstimationSagaConsumer.java`

This file **does not need changes**. It deserializes `PremiumCalculatedEvent` but only accesses `event.getPremium()` and `event.getBreakdown()` — it never accesses `event.getCompanyId()`. The `PremiumCalculatedEvent` class has already had `companyId` removed by plan 01, so the deserialization simply won't include that field. No code change needed here.

Same for `OutboxEventSerializer.java` and `SagaTimeoutService.java` — they don't reference companyId at all.

---

## Part E: Database — Update SQL Init Script

**File:** `infra/sql/estimation_db/init.sql`

### E1. Remove `company_id` from the `estimations` table DDL

**Current (lines 3–17):**
```sql
CREATE TABLE IF NOT EXISTS estimations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID UNIQUE NOT NULL,
    customer_id UUID,
    vehicle_id UUID,
    real_estate_id UUID,
    insurance_type_id INT,
    company_id UUID,                         -- <-- DELETE THIS LINE
    trace_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'COMPLETED', 'REJECTED')),
    premium DECIMAL(12,2),
    details JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Target:**
```sql
CREATE TABLE IF NOT EXISTS estimations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    saga_id UUID UNIQUE NOT NULL,
    customer_id UUID,
    vehicle_id UUID,
    real_estate_id UUID,
    insurance_type_id INT,
    trace_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'COMPLETED', 'REJECTED')),
    premium DECIMAL(12,2),
    details JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### E2. Add ALTER TABLE migration for existing databases

Per AGENTS.md DB schema rules, add after the `CREATE TABLE IF NOT EXISTS` block:

```sql
-- Migration: remove company_id from estimations (multi-company concept removed)
ALTER TABLE estimations DROP COLUMN IF EXISTS company_id;
```

This line can go after the existing `ALTER TABLE estimations ADD COLUMN IF NOT EXISTS trace_id UUID;` line (line 34 in the original file), or just before it.

---

## Part F: Test Files

### F1. EstimationServiceTest.java

**File:** `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/service/EstimationServiceTest.java`

Remove all `companyId` references:
```
Line 50:  private final UUID companyId = UUID.randomUUID();              → DELETE
Line 63:  request.setCompanyId(companyId);                               → DELETE
Line 74:  .companyId(companyId)                                         → DELETE
Line 101: assertThat(response.getCompanyId()).isEqualTo(companyId);     → DELETE
Line 122: request.setCompanyId(companyId);                               → DELETE
```

### F2. EstimationControllerTest.java

**File:** `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/controller/EstimationControllerTest.java`

Remove all `companyId` references:
```
Line 46:  private final UUID companyId = UUID.randomUUID();              → DELETE
Line 62:  .companyId(companyId)                                         → DELETE
Line 78:  request.setCompanyId(companyId);                               → DELETE
Line 120: request.setCompanyId(companyId);                               → DELETE
```

### F3. EstimationTest.java (entity unit test)

**File:** `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/entity/EstimationTest.java`

Remove:
```
Line 49: .companyId(UUID.randomUUID())                                   → DELETE
```

### F4. Files Confirmed to Have NO companyId References

Verified via grep — the following files have zero companyId references and need NO changes:
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceIntegrationTest.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/EstimationServiceApplicationTests.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/e2e/SagaE2ETest.java`
- `services/estimation-service/src/test/java/com/insurancemanagementsystem/estimation/config/MessagePublisherTest.java`

### F5. Files That Do NOT Need Changes

The following estimation-service files have **zero** references to companyId and need no modification:
- `EstimationSagaConsumer.java` — never accesses `companyId` from events
- `OutboxEventSerializer.java` — only builds `EstimationFailedEvent`
- `SagaTimeoutService.java` — operates on `Estimation` entity for timeout logic, never references `companyId`
- `MessagePublisherTest.java` — no company references
- `EstimationRepository.java` — has no `findByCompanyId` method

---

## Acceptance Criteria

- [x] `Estimation.java` entity has no `companyId` field
- [x] `EstimationRequest.java` DTO has no `companyId` field — request payload no longer requires it
- [x] `EstimationResponse.java` DTO has no `companyId` field — response no longer includes it
- [x] `EstimationService.create()` builds entity and event without `companyId`
- [x] `infra/sql/estimation_db/init.sql` has no `company_id` column in `estimations` table, plus `ALTER TABLE ... DROP COLUMN IF EXISTS` migration
- [x] All test files compile and pass: `./gradlew :estimation-service:test`
- [x] `grep -ri "companyid\|company_id\|insurancecompany\|insurance_company" services/estimation-service/src/` returns zero results
- [x] Service compiles: `./gradlew :estimation-service:compileJava`
