# Task: Phase 2 — Message Queue & Event-Driven Integration

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/03_SAGA_PATTERN.md
- Read Blueprint: @docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md

## Objective
Finalize and harden the entire event-driven infrastructure. Covers schema finalization, infrastructure provisioning, shared library, end-to-end SAGA testing, DLQ handling, and distributed tracing.

### Subtasks

1. **Finalize All Event Schemas**
   - Review and finalize all event POJOs in `common-message` module with all SAGA participants.
   - Update schema registry with any missing fields or corrections discovered during service implementation.
   - Add serialization/deserialization unit tests for every event type.

2. **Provision Message Infrastructure**
   - IaC scripts (or Docker Compose extras) to create all Kafka topics with correct partitions and retention:
     - `estimation.saga` — 3 partitions, 7-day retention, no compaction.
     - `customer.events`, `vehicle.events`, `realestate.events`, `insurance.events`, `reference-data.events` — 2 partitions each, 30-day retention, log-compacted.
   - RabbitMQ: `rpc-exchange` (direct), `rpc.reference-data` queue, `dlq.saga` queue.

3. **Build Common Message Library**
   - Build a reusable `common-message` module (published as a Gradle artifact or included as a Git submodule).
   - Abstractions: `MessagePublisher<T>`, `MessageListener<T>` with implementations for Kafka and RabbitMQ.
   - All event POJOs with JSON serialization support.
   - Correlation utilities: `CorrelationIdGenerator`, `SagaContext` holder.

4. **End-to-End SAGA Tests**
   - Integration test that spins up all services (or their messaging layers) with Testcontainers (PostgreSQL × N, Kafka, RabbitMQ).
   - Happy path: create estimation → validate customer + vehicle → calculate premium → complete.
   - Idempotency test: publish duplicate events → verify no side effects.
   - Timeout test: estimation with no response → verify `EstimationFailed` published within timeout window.
   - DLQ test: poison message → verify it lands in `dlq.saga`.

5. **Implement Dead Letter Queue Handling**
   - RabbitMQ dead letter exchange/queue configuration on `dlq.saga`.
   - Retry consumer with exponential backoff (1s, 2s, 4s, 8s, max 5 retries).
   - After max retries: log the failed message, notify admin channel, do not consume again.

6. **Setup Distributed Tracing**
   - Add Spring Cloud Sleuth (or Micrometer Tracing) to all services.
   - Configure trace propagation via Kafka headers and RabbitMQ message headers.
   - Zipkin collector receiving traces from all services.
   - Each log entry includes `traceId`, `sagaId`, `eventId` as structured JSON fields.

### Deliverables
- Finalized event schemas and schema registry
- Kafka topics and RabbitMQ queues provisioned
- `common-message` shared library with publishers, listeners, event POJOs
- End-to-end SAGA integration test suite passing
- DLQ with retry mechanism operational
- Distributed tracing (Zipkin) capturing all service interactions
- CI pipeline runs e2e SAGA tests on every push to `develop`
