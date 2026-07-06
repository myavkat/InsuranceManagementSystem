# Subtask 1: Finalize All Event Schemas

## Status: NOT STARTED
## Parent: `07_PHASE2_MASTER_PLAN.md`
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Review and finalize all event POJOs in `common-message` module. Ensure every SAGA and domain event type has:
1. Complete fields needed by producers and consumers
2. Serialization/deserialization unit tests (round-trip JSON)
3. Consistent patterns across all event types
4. A schema registry entry or documentation of the schema

## Files to Read Before Starting

1. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/BaseEvent.java`
2. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventEnvelope.java`
3. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/EventConstants.java`
4. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/saga/*.java` (all 10 SAGA events)
5. `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/*.java` (all 12 domain events)
6. `common/common-message/src/test/java/com/insurancemanagementsystem/common/event/EventSerializationTest.java`
7. `docs/outlines/03_SAGA_PATTERN.md` — event catalog, event flow
8. `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` — per-service consumer/producer specs
9. `docs/outlines/10_JAVA_CONVENTIONS.md` — Lombok order, Jackson 3 notes

## Current State

### What Exists
- `EventEnvelope` — common wrapper with sagaId, eventType, timestamp, traceId, payload
- `BaseEvent` — abstract base with `getEventType()`, `toEnvelope()`, `toJson()`, `fromJson()`
- 10 SAGA event POJOs: EstimationRequested, CustomerValidated, CustomerInvalidated, VehicleValidated, VehicleInvalidated, RealEstateValidated, RealEstateInvalidated, PremiumCalculated, CalculationFailed, EstimationFailed
- 12 domain event POJOs: CustomerCreated/Updated/Deleted, VehicleCreated/Updated/Deleted, RealEstateCreated/Updated/Deleted, InsuranceCreated/Updated, ReferenceDataChanged
- `EventConstants` — all topic names and event type strings
- `EventSerializationTest` — covers only 3 of 10 SAGA event types + envelope

### What's Missing
- Serialization tests for all event types (currently only 3 SAGA events tested)
- Schema documentation per event type
- Some domain events have sparse fields (e.g., `RealEstateCreatedEvent` only has `realEstateId`)
- No `InsuranceDeletedEvent` domain event exists
- No verification that all fields needed by consumers are present on events

---

## Implementation Steps

### Step 1: Audit All Event POJOs Against Consumer Needs

- [ ] **1.1** Open each service's SAGA consumer and list what fields they extract from each event:
  - `services/estimation-service/.../EstimationSagaConsumer.java` — reads CustomerValidatedEvent, VehicleValidatedEvent, PremiumCalculatedEvent, etc.
  - `services/insurance-service/.../InsuranceSagaConsumer.java` — reads EstimationRequestedEvent, CustomerValidatedEvent, VehicleValidatedEvent
  - `services/customer-service/.../` — find the SAGA consumer, check what it publishes in CustomerValidatedEvent
  - `services/vehicle-service/.../` — find the SAGA consumer, check what it publishes in VehicleValidatedEvent

- [ ] **1.2** Create an audit table (in this plan) listing each event type with:
  - All fields currently on the POJO
  - All fields consumed by downstream services
  - Any missing fields
  - Action: "OK", "ADD field X", or "REMOVE unused field Y"

### Step 2: Add Missing Event Types

- [ ] **2.1** Create `InsuranceDeletedEvent.java`:
  - Path: `common/common-message/src/main/java/com/insurancemanagementsystem/common/event/domain/InsuranceDeletedEvent.java`
  - Pattern: same as other domain events (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@EqualsAndHashCode(callSuper = true)`)
  - Fields: `UUID insuranceId`, `Integer typeId`, `UUID companyId`
  - `getEventType()` returns `EventConstants.INSURANCE_DELETED`

- [ ] **2.2** Add `INSURANCE_DELETED = "InsuranceDeleted"` constant to `EventConstants.java`

### Step 3: Fill Sparse Domain Event Fields

- [ ] **3.1** Review each domain event for completeness. The following events are known to have minimal fields:
  - `RealEstateCreatedEvent` — has only `realEstateId`. Should include: `address`, `cityId`, `customerId`
  - `RealEstateUpdatedEvent` — has only `realEstateId`. Should include changed fields
  - `RealEstateDeletedEvent` — has only `realEstateId`. OK for delete
  - `VehicleCreatedEvent` — has `vehicleId` and `plate`. Should also include: `customerId`, `carBrandId`
  - `VehicleUpdatedEvent` — has `vehicleId` and `plate`. Evaluate if more fields needed
  - `CustomerCreatedEvent` — has `customerId`, `nationalId`, `email`. Should include: `firstName`, `lastName`
  - `CustomerUpdatedEvent` — has `customerId`, `nationalId`, `email`. Same additions
  - `CustomerDeletedEvent` — has `customerId`, `nationalId`. OK for delete

- [ ] **3.2** Look at the actual entities in each service to determine the right field set:
  - `services/customer-service/.../entity/Customer.java` — check record fields
  - `services/vehicle-service/.../entity/Vehicle.java` — check record fields
  - `services/realestate-service/.../entity/RealEstate.java` — check record fields

- [ ] **3.3** Add necessary fields to each domain event. Keep fields minimal but useful — domain events are for audit/analytics/cache invalidation.

### Step 4: Add Comprehensive Serialization Tests

- [ ] **4.1** Extend `EventSerializationTest.java` to cover ALL event types:

  For each SAGA event type (10 tests):
  - Build the event with realistic test data
  - Serialize to JSON via `event.toJson()`
  - Deserialize via `BaseEvent.fromJson(json, EventClass.class)`
  - Assert all fields round-trip correctly
  - Build an `EventEnvelope` wrapping the event via `event.toEnvelope(sagaId, traceId)`
  - Serialize the envelope to JSON via `jsonMapper.writeValueAsString(envelope)`
  - Deserialize envelope via `jsonMapper.readValue(json, EventEnvelope.class)`
  - Assert envelope fields (sagaId, eventType, timestamp, traceId) survive
  - Assert payload deserializes back to the original event type via `jsonMapper.convertValue(envelope.getPayload(), EventClass.class)`

  For each domain event type (13 tests after adding InsuranceDeleted):
  - Build the event with realistic test data
  - Serialize/deserialize round-trip
  - Verify `getEventType()` returns correct constant

- [ ] **4.2** Add edge case tests:
  - `PremiumCalculatedEvent` with null breakdown map — verify serializes as null, not "null" string
  - `PremiumCalculatedEvent` with BigDecimal values having many decimal places — verify precision preserved
  - Event with null optional fields (e.g., `realEstateId` in EstimationRequestedEvent) — verify null handling
  
- [ ] **4.3** Add a test that verifies `EventConstants` event type strings match actual `getEventType()` return values for every event class

### Step 5: Document Event Schemas

- [ ] **5.1** Create `docs/outlines/14_EVENT_SCHEMA_REGISTRY.md` with a table per event:
  ```
  ## EstimationRequestedEvent
  | Field | Type | Required | Description |
  |-------|------|----------|-------------|
  | customerId | UUID | Yes | Customer being quoted |
  | vehicleId | UUID | No | Vehicle if traffic insurance |
  | realEstateId | UUID | No | Real estate if property insurance |
  | insuranceTypeId | Integer | Yes | Type of insurance from reference data |
  | companyId | UUID | Yes | Insurance company |
  ```
  Do this for ALL 23 event types (10 SAGA + 13 domain).

- [ ] **5.2** Document the envelope structure at the top of the registry

### Step 6: Build and Verify

- [ ] **6.1** Build common-message: `.\gradlew.bat :common:common-message:build`
- [ ] **6.2** Run tests: `.\gradlew.bat :common:common-message:test`
- [ ] **6.3** Fix any compilation errors in downstream services due to field changes
- [ ] **6.4** Build all affected services to verify no breakage: `.\gradlew.bat build`

---

## Files to Create

| File | Purpose |
|------|---------|
| `common/common-message/src/main/java/.../event/domain/InsuranceDeletedEvent.java` | New domain event |
| `docs/outlines/14_EVENT_SCHEMA_REGISTRY.md` | Schema documentation |

## Files to Modify

| File | Change |
|------|--------|
| `common/common-message/src/main/java/.../event/EventConstants.java` | Add `INSURANCE_DELETED` constant |
| `common/common-message/src/main/java/.../event/domain/RealEstateCreatedEvent.java` | Add `address`, `cityId`, `customerId` fields |
| `common/common-message/src/main/java/.../event/domain/RealEstateUpdatedEvent.java` | Add relevant fields |
| `common/common-message/src/main/java/.../event/domain/VehicleCreatedEvent.java` | Add `customerId`, `carBrandId` fields |
| `common/common-message/src/main/java/.../event/domain/CustomerCreatedEvent.java` | Add `firstName`, `lastName` fields |
| `common/common-message/src/main/java/.../event/domain/CustomerUpdatedEvent.java` | Add `firstName`, `lastName` fields |
| `common/common-message/src/test/java/.../event/EventSerializationTest.java` | Extend to cover all 23 event types |

## Patterns to Follow

### Event POJO Pattern
```java
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XxxEvent extends BaseEvent {
    private UUID someId;
    private String someField;

    @Override
    public String getEventType() {
        return EventConstants.XXX;
    }
}
```

### Serialization Test Pattern
```java
@Test
void shouldSerializeAndDeserializeXxxEvent() {
    XxxEvent event = XxxEvent.builder()
            .someId(UUID.randomUUID())
            .someField("value")
            .build();

    String json = event.toJson();
    assertThat(json).contains("\"someField\":\"value\"");

    XxxEvent deserialized = BaseEvent.fromJson(json, XxxEvent.class);
    assertThat(deserialized.getSomeId()).isEqualTo(event.getSomeId());
    assertThat(deserialized.getEventType()).isEqualTo(EventConstants.XXX);

    // Envelope round-trip
    EventEnvelope envelope = event.toEnvelope(sagaId, traceId);
    String envelopeJson = jsonMapper.writeValueAsString(envelope);
    EventEnvelope deserializedEnvelope = jsonMapper.readValue(envelopeJson, EventEnvelope.class);
    assertThat(deserializedEnvelope.getSagaId()).isEqualTo(sagaId);
    assertThat(deserializedEnvelope.getEventType()).isEqualTo(EventConstants.XXX);
    XxxEvent payload = jsonMapper.convertValue(deserializedEnvelope.getPayload(), XxxEvent.class);
    assertThat(payload.getSomeId()).isEqualTo(event.getSomeId());
}
```

## Dependencies
- None (this is the foundational subtask)

## Completion Criteria
- [ ] All 23 event types have serialization round-trip tests passing
- [ ] All event POJOs have complete fields matching consumer needs
- [ ] `InsuranceDeletedEvent` exists with constant
- [ ] Schema registry document is complete
- [ ] `.\gradlew.bat build` passes for common-message and all services
