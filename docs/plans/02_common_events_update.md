# Plan 02: Common Message Events — Replace insuranceTypeId with insuranceId

## Objective

Update the shared event POJOs in `common-message` that carry `insuranceTypeId` to carry `insuranceId` instead. This affects two SAGA event classes and their serialization test.

## Dependencies

- **None.** Can run in parallel with Plan 01 (Database Migration).
- **Required by:** Plan 03 (Estimation Backend), Plan 04 (Insurance SAGA Consumer).

## Files to Read Before Starting

- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/EstimationRequestedEvent.java`
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/PremiumCalculatedEvent.java`
- `common/common-message/src/test/java/com/insurancemanagementsystem/common/event/EventSerializationTest.java`
- `docs/outlines/14_EVENT_SCHEMA_REGISTRY.md` — sections on `EstimationRequestedEvent` and `PremiumCalculatedEvent`

## Technical Context

### Convention: Lombok order
From `docs/outlines/10_JAVA_CONVENTIONS.md`: Use Lombok annotations in this order on entity/DTO classes:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
```
The existing event classes already follow this. Keep it.

### Convention: UUID type
`insuranceId` is a `UUID`, not an `Integer`. The old `insuranceTypeId` was `Integer`.

### Convention: Event field additions require producer updates
From `AGENTS.md`: "When adding fields to event POJOs in common-message, the plan checklist MUST include a step to update all producers." Since we're replacing a field (not adding), producers that reference the old field name will fail to compile — the compiler will guide the updates in Plan 03 and 04.

### What uses these events

**EstimationRequestedEvent**:
- **Producer:** `EstimationService.create()` in estimation-service (Plan 03)
- **Consumer:** `InsuranceSagaConsumer.handleEstimationRequested()` in insurance-service (Plan 04)

**PremiumCalculatedEvent**:
- **Producer:** `InsuranceSagaConsumer.calculatePremium()` in insurance-service (Plan 04)
- **Consumer:** `EstimationSagaConsumer.handlePremiumCalculated()` in estimation-service (Plan 03)

## Steps

### Step 1: Update EstimationRequestedEvent.java

Open `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/EstimationRequestedEvent.java`.

**Change this field:**
```java
private Integer insuranceTypeId;
```
**To:**
```java
private UUID insuranceId;
```

The complete file after the change should look like:
```java
package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimationRequestedEvent extends BaseEvent {
    private UUID customerId;
    private UUID vehicleId;
    private UUID realEstateId;
    private UUID insuranceId;

    @Override
    public String getEventType() {
        return EventConstants.ESTIMATION_REQUESTED;
    }
}
```

### Step 2: Update PremiumCalculatedEvent.java

Open `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/PremiumCalculatedEvent.java`.

**Change this field:**
```java
private Integer insuranceTypeId;
```
**To:**
```java
private UUID insuranceId;
```

The complete file after the change should look like:
```java
package com.insurancemanagementsystem.common.event.saga;

import com.insurancemanagementsystem.common.event.BaseEvent;
import com.insurancemanagementsystem.common.event.EventConstants;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumCalculatedEvent extends BaseEvent {
    private BigDecimal premium;
    private Map<String, BigDecimal> breakdown;
    private UUID insuranceId;
    private UUID customerId;
    private UUID vehicleId;

    @Override
    public String getEventType() {
        return EventConstants.PREMIUM_CALCULATED;
    }
}
```

### Step 3: Update EventSerializationTest.java

Open `common/common-message/src/test/java/com/insurancemanagementsystem/common/event/EventSerializationTest.java`.

There are **6 occurrences** of `.insuranceTypeId(` across 6 test methods. Every one must be updated.

**A. Line 37 — `shouldSerializeAndDeserializeEstimationRequestedEvent`:**
```java
.insuranceTypeId(1)     // OLD
```
→
```java
.insuranceId(UUID.randomUUID())     // NEW
```

**B. Line 112 — `shouldSerializeAndDeserializePremiumCalculatedEvent`:**
```java
.insuranceTypeId(1)     // OLD
```
→
```java
.insuranceId(UUID.randomUUID())     // NEW
```

**C. Line 298 — `shouldHandlePremiumCalculatedWithNullBreakdown`:**
```java
.insuranceTypeId(1)     // OLD
```
→
```java
.insuranceId(UUID.randomUUID())     // NEW
```

**D. Line 315 — `shouldPreserveBigDecimalPrecisionWithManyDecimalPlaces`:**
```java
.insuranceTypeId(1)     // OLD
```
→
```java
.insuranceId(UUID.randomUUID())     // NEW
```

**E. Line 336 — `shouldHandleNullOptionalFieldsInEstimationRequested`:**
```java
.insuranceTypeId(3) // DASK     // OLD
```
→
```java
.insuranceId(UUID.randomUUID()) // DASK     // NEW
```

Also update the assertion on line 345:
```java
assertEquals(event.getInsuranceTypeId(), deserialized.getInsuranceTypeId());   // OLD
```
→
```java
assertEquals(event.getInsuranceId(), deserialized.getInsuranceId());   // NEW
```

**F. Line 440 — `shouldSerializeAndDeserializeEventEnvelope`:**
```java
.insuranceTypeId(2)     // OLD
```
→
```java
.insuranceId(UUID.randomUUID())     // NEW
```

Also update the assertion on line 460:
```java
assertEquals(payload.getInsuranceTypeId(), convertedPayload.getInsuranceTypeId());   // OLD
```
→
```java
assertEquals(payload.getInsuranceId(), convertedPayload.getInsuranceId());   // NEW
```

**G. Lines 472, 480 — `shouldRoundTripViaBaseEventUtilities`:**
```java
.insuranceTypeId(1)     // OLD (line 472)
```
→
```java
.insuranceId(UUID.randomUUID())     // NEW
```
```java
assertEquals(event.getInsuranceTypeId(), deserialized.getInsuranceTypeId());   // OLD (line 480)
```
→
```java
assertEquals(event.getInsuranceId(), deserialized.getInsuranceId());   // NEW
```

### Step 4: Verify compilation

From the repository root, run:
```bash
cd common/common-message && ./gradlew compileJava compileTestJava
```

If there are compilation errors, they will point to any remaining references to `insuranceTypeId` on these two event classes. Fix them.

## Acceptance Criteria

- [x] `EstimationRequestedEvent.insuranceTypeId` (Integer) replaced with `insuranceId` (UUID)
- [x] `PremiumCalculatedEvent.insuranceTypeId` (Integer) replaced with `insuranceId` (UUID)
- [x] `EventSerializationTest.java` updated to use `insuranceId` with UUID values
- [x] `./gradlew compileJava compileTestJava` passes in `common/common-message`
