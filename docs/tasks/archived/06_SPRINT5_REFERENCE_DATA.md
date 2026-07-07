# Task: Sprint 5 — Reference Data Service

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md
- Read Blueprint: @docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md
- Read Story: @docs/stories/07_REFERENCE_DATA.md

## Objective
Implement the Reference Data Service — a lightweight service that owns city, profession, and other lookup tables. Serves data via REST API (through the API Gateway, matching the pattern of all other services). Publishes domain events to Kafka for cache invalidation.

### Subtasks

1. **Extract Reference Data Domain**
   - Create `services/reference-data-service/` from the reference skeleton.
   - Entities: `City` (id, name, plateCode), `Profession` (id, name).
   - JPA repositories with sorted lookups.

2. **Apply Reference DB Scripts**
   - Run the `reference_data_db` init SQL against PostgreSQL.
   - Seed: 81 Turkish cities with plate codes, common professions list.

3. **Implement Reference Data REST API**
   - `GET /api/reference-data/cities` — alphabetically sorted list of cities with plate codes.
   - `GET /api/reference-data/professions` — alphabetically sorted list of professions.
   - Response caching headers (Redis or in-memory with TTL).
   - Publish domain events to `reference-data.events` on data changes.

### Deliverables
- Reference Data Service with REST API
- Seed data for 81 cities and professions
- In-memory caching with TTL
- Kafka domain event publishing on `reference-data.events`
- Unit tests (≥80% coverage)
- Integration tests for REST endpoints
