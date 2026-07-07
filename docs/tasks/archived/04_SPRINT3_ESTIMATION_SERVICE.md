# Task: Sprint 3 — Estimation Service (SAGA Core)

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md
- Read Blueprint: @docs/outlines/03_SAGA_PATTERN.md
- Read Story: @docs/stories/04_ESTIMATION_SAGA.md

## Objective
Implement the Estimation Service — the SAGA choreography coordinator. Generates `sagaId`, publishes `EstimationRequested`, listens for terminal events, manages estimation state machine, and enforces timeout compensation.

### Subtasks

1. **Extract Estimation Domain**
   - Create `services/estimation-service/` from the reference skeleton.
   - Entity: `Estimation` (id, sagaId, customerId, vehicleId nullable, realEstateId nullable, insuranceTypeId, companyId, status [STARTED, COMPLETED, REJECTED], premium, details JSON, createdAt, updatedAt).
   - JPA repository with query methods by customerId, status, date range, and sagaId.

2. **Apply Estimation DB Scripts**
   - Run the `estimation_db` init SQL against PostgreSQL.

3. **Estimation CRUD API**
   - `POST /api/estimations` — create estimation (validates inputs, generates sagaId, starts SAGA).
   - `GET /api/estimations/{id}` — get estimation with current status and premium.
   - `GET /api/estimations` — list with filters: customerId, status, date range, paginated.

4. **Implement SAGA Choreography Handlers**
   - First, generate `sagaId` (UUID) and create Estimation with status `STARTED`.
   - Publish `EstimationRequested` to `estimation.saga` with sagaId, customerId, vehicleId, insuranceTypeId, companyId in payload.
   - Consume all terminal events:
     - `CustomerValidated` / `VehicleValidated` — log progress, persist correlation.
     - `CustomerInvalidated` / `VehicleInvalidated` / `CalculationFailed` — transition to `REJECTED`, publish `EstimationFailed`.
     - `PremiumCalculated` — transition to `COMPLETED` with premium details.
   - Maintain saga state machine in DB (status transitions: STARTED → COMPLETED or REJECTED).

5. **Implement SAGA Compensation Logic**
   - Timeout scheduler: `@Scheduled` fixed-delay task that queries estimations in `STARTED` status older than N minutes (configurable, default 5).
   - On timeout: set estimation to `REJECTED`, publish `EstimationFailed`.
   - `EstimationFailed` is published to `estimation.saga` for all participating services.

6. **Estimation Messaging Integration**
   - All events use the shared `common-message` event POJOs.
   - Idempotency: deduplicate incoming events by `(sagaId, eventType)` in local dedup table.

### Deliverables
- Fully functional Estimation Service with CRUD API
- Complete SAGA choreography state machine
- Timeout-based compensation
- Idempotent event consumers
- Unit tests (≥80% coverage)
- Integration tests covering happy path, validation failures, timeout, duplicate events
