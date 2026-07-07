# Task: Sprint 1 — Customer Service

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md
- Read Blueprint: @docs/outlines/03_SAGA_PATTERN.md
- Read Story: @docs/stories/02_CUSTOMER_MANAGEMENT.md

## Objective
Extract and implement the Customer Service — the first microservice carved from the monolith. Covers domain extraction, CRUD API, messaging, and SAGA participation.

### Subtasks

1. **Extract Customer Domain**
   - Create `services/customer-service/` from the reference skeleton.
   - Add `Customer` entity: id, firstName, lastName, nationalId (TCKN), email, phone, birthDate, address, cityId, professionId, createdAt, updatedAt, deletedAt.
   - Create JPA repository with search methods (by name, nationalId).
   - Implement service layer with business validation rules.

2. **Apply Customer DB Scripts**
   - Run the `customer_db` init SQL from Phase 0 against PostgreSQL.
   - Verify tables and seed data (if any) are correctly applied.

3. **Implement Customer CRUD API**
   - `GET /api/customers` — paginated list with search/filter by name and nationalId.
   - `GET /api/customers/{id}` — single customer detail.
   - `POST /api/customers` — create with validation.
   - `PUT /api/customers/{id}` — update.
   - `DELETE /api/customers/{id}` — soft-delete (sets `deletedAt`).
   - Standard `ApiResponse<T>` envelope on all endpoints.

4. **Add Customer Messaging**
   - Add Spring Cloud Stream Kafka binder for domain event publishing.
   - Add RabbitMQ binder for RPC call support (future reference data lookups).

5. **Publish Customer Domain Events**
   - Publish `CustomerCreated` on entity creation.
   - Publish `CustomerUpdated` on entity update.
   - Topic: `customer.events` (log-compacted, keyed by customerId).

6. **Implement Customer Saga Consumers**
   - Consume `EstimationRequested` from `estimation.saga`.
   - Validate customer existence and active status.
   - Publish `CustomerValidated` or `CustomerInvalidated` to `estimation.saga`.
   - Idempotency guard: deduplicate by `(sagaId, eventType)`.

### Deliverables
- Fully functional Customer Service with CRUD API
- Domain events publishing on customer changes
- SAGA consumer for estimation validation
- Unit tests for service layer (≥80% coverage)
- Integration tests with Testcontainers (PostgreSQL + Kafka)
