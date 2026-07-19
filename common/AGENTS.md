# Common Modules AGENTS.md

## Overview
Shared libraries used across microservices to prevent code duplication and ensure consistent event schemas and web configurations.

## Workflow Commands
Building and Testing:
1. Build the common modules:
   ```bash
   ./gradlew :common:common-message:build
   ./gradlew :common:common-test:build
   ```
2. Run tests:
   ```bash
   ./gradlew :common:common-message:test
   ```

## Cross-Service Code Sharing Rules
- **Extract before triplicating**: Before adding the same class to a 3rd service module, extract it to `common-message`. If it already exists in 2 services, the next change must be extraction, not copy-paste.
- **Propagate trace context**: All outbound SAGA events must carry the original `traceId` from the triggering `EventEnvelope`. Never generate a fresh `UUID.randomUUID()` for outbound traceId.
- **New abstractions require adoption before completion**: Any new shared class added to `common-message` (listener base classes, context holders, publisher wrappers, test base classes) MUST be adopted by at least one service before the task is marked complete.
- **Event field additions require producer updates**: When adding fields to event POJOs in `common-message`, the plan checklist MUST include a step to update all producers that publish those event types.

## Event Schema Registry
- All events travel through the message broker wrapped in a common `EventEnvelope` containing `sagaId`, `eventType`, `timestamp`, `traceId`, and `payload`.
- SAGA events flow through the `estimation.saga` Kafka topic.
- Domain events flow through per-service topics (`customer.events`, `vehicle.events`, etc.) for audit, analytics, and cache invalidation.
