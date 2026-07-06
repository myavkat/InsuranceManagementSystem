# Plan: Sprint 4 — Vehicle & RealEstate — Step 1: Common-Message RealEstate SAGA Events

## Objective
Add `RealEstateValidatedEvent` and `RealEstateInvalidatedEvent` SAGA event classes to `common-message`, plus their event type constants in `EventConstants.java`. These events are needed by the RealEstate Service's SAGA consumer to participate in the estimation flow.

## Context Files to Read First
1. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleValidatedEvent.java`** — Exact pattern to copy for RealEstateValidatedEvent
2. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/VehicleInvalidatedEvent.java`** — Exact pattern to copy for RealEstateInvalidatedEvent
3. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`** — Where to add the new constants
4. **`common/common-message/src/main/java/com/insurancemanagementsystem/common/event/BaseEvent.java`** — Base class both events extend

## Files to Create

### 1. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/RealEstateValidatedEvent.java`

Copy the exact pattern from `VehicleValidatedEvent.java`. Fields: `UUID realEstateId`, `String address`, `Integer cityId`.

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
public class RealEstateValidatedEvent extends BaseEvent {
    private UUID realEstateId;
    private String address;
    private Integer cityId;

    @Override
    public String getEventType() {
        return EventConstants.REAL_ESTATE_VALIDATED;
    }
}
```

### 2. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/RealEstateInvalidatedEvent.java`

Copy the exact pattern from `VehicleInvalidatedEvent.java`. Fields: `UUID realEstateId`, `String reason`.

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
public class RealEstateInvalidatedEvent extends BaseEvent {
    private UUID realEstateId;
    private String reason;

    @Override
    public String getEventType() {
        return EventConstants.REAL_ESTATE_INVALIDATED;
    }
}
```

### 3. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`

Add two new constants in the SAGA event types section (near the existing `VEHICLE_VALIDATED` / `VEHICLE_INVALIDATED` constants around lines 19-20):

```java
public static final String REAL_ESTATE_VALIDATED = "RealEstateValidated";
public static final String REAL_ESTATE_INVALIDATED = "RealEstateInvalidated";
```

Insert them after the existing Vehicle lines so the section reads:

```java
// SAGA event types
public static final String ESTIMATION_REQUESTED = "EstimationRequested";
public static final String CUSTOMER_VALIDATED = "CustomerValidated";
public static final String CUSTOMER_INVALIDATED = "CustomerInvalidated";
public static final String VEHICLE_VALIDATED = "VehicleValidated";
public static final String VEHICLE_INVALIDATED = "VehicleInvalidated";
public static final String REAL_ESTATE_VALIDATED = "RealEstateValidated";
public static final String REAL_ESTATE_INVALIDATED = "RealEstateInvalidated";
public static final String PREMIUM_CALCULATED = "PremiumCalculated";
public static final String CALCULATION_FAILED = "CalculationFailed";
public static final String ESTIMATION_FAILED = "EstimationFailed";
```

## Key Conventions
- Java 25, Lombok: `@EqualsAndHashCode(callSuper = true) @Data @Builder @NoArgsConstructor @AllArgsConstructor`
- Extend `BaseEvent`, override `getEventType()` to return the constant from `EventConstants`
- Jackson 3: `tools.jackson.databind.json.JsonMapper` (no import needed in these classes — they only use Lombok + BaseEvent)
- Follow the EXACT pattern of `VehicleValidatedEvent` — same annotations, same structure, same formatting

## Verification

```bash
.\gradlew.bat :common:common-message:build
```

Should compile successfully and pass existing serialization tests.

## Files Written
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/RealEstateValidatedEvent.java` (NEW)
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/RealEstateInvalidatedEvent.java` (NEW)
- `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java` (MODIFIED — 2 lines added)
