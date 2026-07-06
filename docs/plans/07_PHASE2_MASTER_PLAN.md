# Phase 2 Master Plan — Message Queue & Event-Driven Integration

## Status: PLANNING COMPLETE — Ready for Implementation

## Branch: `phase2-message-queue-event-driven-integration`

---

## Overview

This master plan coordinates 6 subtask plans for finalizing the entire event-driven infrastructure of the Insurance Management System. Each subtask has its own detailed plan file that is self-contained for agents with smaller context windows.

## Context Anchors (Read Before Starting Any Subtask)

- `docs/outlines/01_SYSTEM_ARCHITECTURE.md` — tech stack, microservice breakdown, communication architecture
- `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` — per-service entities, endpoints, SAGA consumers
- `docs/outlines/03_SAGA_PATTERN.md` — SAGA choreography flow, event catalog, idempotency, timeout, compensation
- `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md` — Kafka topics, RabbitMQ queues, per-service config
- `docs/outlines/07_PROJECT_STRUCTURE.md` — directory layout, technology stack per layer, build order
- `docs/outlines/10_JAVA_CONVENTIONS.md` — Java 21+ conventions (Instant/LocalDate), Lombok order, Jackson 3 notes
- `docs/outlines/11_TESTING_CONVENTIONS.md` — RestTestClient, slice/integration tests, assertion rules
- `docs/outlines/12_DEVELOPER_COMMANDS.md` — build/test/run commands
- `docs/outlines/13_ENVIRONMENT_QUIRKS.md` — port allocation, Testcontainers on Windows
- `AGENTS.md` — global execution constraints, SAGA/Outbox/DB safety rules
- `docs/AGENTS.md` — workspace context & workflow, directory schema, git branching rule

## Build Order (Dependency Chain)

```
common-message → [All services depend on it]
  → Subtask 1 (Event Schemas) must complete first
  → Subtask 3 (Common Library) must complete first
  → Subtask 2 (Infrastructure) can run in parallel with 1 & 3
  → Subtask 5 (DLQ) depends on 2 & 3
  → Subtask 6 (Tracing) depends on 3
  → Subtask 4 (E2E Tests) depends on ALL others
```

## Subtask Plans

| # | Plan File | Description | Dependencies | Status |
|---|-----------|-------------|--------------|--------|
| 1 | `07_PHASE2_SUBTASK1_EVENT_SCHEMAS.md` | Finalize All Event Schemas | None | [ ] |
| 2 | `07_PHASE2_SUBTASK2_MESSAGE_INFRASTRUCTURE.md` | Provision Message Infrastructure | None | [ ] |
| 3 | `07_PHASE2_SUBTASK3_COMMON_LIBRARY.md` | Build Common Message Library | Subtask 1 | [ ] |
| 4 | `07_PHASE2_SUBTASK4_E2E_SAGA_TESTS.md` | End-to-End SAGA Tests | Subtasks 1,2,3,5,6 | [ ] |
| 5 | `07_PHASE2_SUBTASK5_DLQ_HANDLING.md` | Implement Dead Letter Queue Handling | Subtasks 2,3 | [ ] |
| 6 | `07_PHASE2_SUBTASK6_DISTRIBUTED_TRACING.md` | Setup Distributed Tracing | Subtask 3 | [ ] |

## Execution Order

1. **First:** Subtask 1 (Event Schemas) — ensures all events are finalized before others build on them
2. **In parallel:** Subtask 2 (Infrastructure) + Subtask 3 (Common Library) — independent tracks
3. **Then:** Subtask 5 (DLQ) + Subtask 6 (Tracing) — can run in parallel after their deps
4. **Finally:** Subtask 4 (E2E Tests) — validates everything together

## Global Rules (Apply to ALL Subtasks)

- **Branch:** All work on `phase2-message-queue-event-driven-integration`
- **Commit convention:** `feat(scope):`, `fix(scope):`, `test(scope):`, `refactor(scope):`, `docs:`, `chore:` — topic-by-topic
- **No auto-attribution** in commits
- **Never push to remote** unless explicitly asked
- **Update plan checkboxes** (`- [ ]` → `- [x]`) as each step completes
- **Read the subtask's own plan file** before starting work on it
- **Cross-reference** code against the active plan after each step

## Pre-Existing State Summary

### Already Built (common-message module):
- `EventEnvelope` (sagaId, eventType, timestamp, traceId, payload)
- `BaseEvent` abstract class with `toEnvelope()`, `toJson()`, `fromJson()`
- All 8 SAGA event POJOs + all 12 domain event POJOs
- `EventConstants` with all topic/event-type string constants
- `MessagePublisher` wrapping `StreamBridge.send()`
- `SagaEvent` entity + `SagaEventRepository` with atomic `tryInsertDedup()` (INSERT ON CONFLICT DO NOTHING)
- `OutboxEvent` entity + `OutboxEventRepository` (SELECT FOR UPDATE SKIP LOCKED)
- `OutboxProcessor` + `OutboxRelay` (scheduled transactional outbox delivery)
- `CommonPersistenceAutoConfiguration`
- Unit tests: `EventSerializationTest`, `OutboxProcessorTest`, `SagaEventRepositoryTest`

### Already Built (Services):
- All 6 domain services have SAGA consumers, outbox tables, and saga_events tables
- Estimation service: full SAGA coordinator (create, consume, timeout)
- Insurance service: full SAGA consumer with DB-backed aggregation store
- Customer/Vehicle/RealEstate services: SAGA consumers for validation events
- All services use Spring Cloud Stream Kafka binder with functional bean bindings

### Already Built (Infrastructure):
- Docker Compose with PostgreSQL × 8, Kafka (KRaft), RabbitMQ, Redis
- SQL init scripts for all databases with saga_events and outbox_events tables
- Kafka auto-creates topics via Spring Cloud Stream dynamicDestinations

### Missing (What These Plans Build):
- No `CorrelationIdGenerator`, `SagaContext` holder, `MessageListener<T>` abstraction
- No Kafka topic creation scripts with correct partitions/retention/compaction
- No DLQ topic, retry consumer, exponential backoff
- No Zipkin/Micrometer Tracing/Sleuth in any service
- No end-to-end SAGA integration tests with Testcontainers (Kafka + PostgreSQL)
- Serialization tests only cover 3 of 10 SAGA event types
- No schema registry integration
