# Task: Phase 0 — Preparation & Base Setup

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md
- Read Blueprint: @docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md

## Objective
Lay down all foundational infrastructure so that subsequent microservice and frontend work can proceed in parallel without coordination bottlenecks.

### Subtasks

1. **Create Monorepo Structure**
   - Set up the monorepo layout with `services/`, `frontend-next/`, `infra/`, `common/` directories.
   - Each service gets its own `build.gradle.kts` and standard directory tree (`src/main/java/...`, `src/test/java/...`).
   - Root `settings.gradle.kts` includes all service subprojects if unified build is desired.

2. **Docker Dev Environment Setup**
   - Docker Compose file covering: PostgreSQL × 8 databases, Kafka (with Zookeeper), RabbitMQ.
   - Health checks, named volumes for data persistence, `.env` file for configurable ports/credentials.
   - One-command startup: `docker compose up -d`.

3. **Initialize Next.js Project**
   - `npx create-next-app@latest frontend-next` with TypeScript, App Router, Tailwind CSS.
   - Add shadcn/ui: `npx shadcn@latest init`.
   - Install Zustand, TanStack React Query, Zod, React Hook Form.
   - Set up `GATEWAY_URL` env var pointing to `http://localhost:8080`.

4. **Database Schema Design**
   - Design all 8 database schemas upfront: `auth_db`, `customer_db`, `vehicle_db`, `realestate_db`, `insurance_db`, `estimation_db`, `reference_data_db`, `gateway_db` (rate limiting).
   - Write all init SQL scripts (`init.sql` per database) with tables, indexes, seed data.
   - Seed: 81 Turkish cities, common professions, insurance types (TRAFFIC, CASCO, DASK, HEALTH, LIFE), car brands/models/engines/fuel-types/types/packages, construction types, luxury classes, usage types, admin user.

5. **Define Event Schemas**
   - Define all SAGA and domain event schemas as JSON payloads in a `common-message` module.
   - Create shared POJO classes for every event type (see `03_SAGA_PATTERN.md` event catalog).
   - Include envelope fields: `sagaId`, `eventType`, `timestamp`, `traceId`, `payload`.

6. **Build Reference Skeleton Service**
   - Build one complete skeleton service under `services/reference-skeleton/` with Spring MVC + JPA + Spring Cloud Stream (Kafka + RabbitMQ binders).
   - Include: entity, repository, service, `@RestController`, `@ControllerAdvice` error handling, standardized `ApiResponse<T>`, application.yml, Dockerfile, Gradle build.
   - This serves as the template from which all other services are copied.

### Deliverables
- Monorepo layout with infrastructure scripts
- Docker Compose environment ready
- Next.js project scaffolded with dependencies
- All 8 database init SQL scripts ready to apply
- `common-message` module with all event POJOs
- Reference skeleton service that other services can fork
