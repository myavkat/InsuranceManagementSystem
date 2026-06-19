# System Migration Plan

## III. Detailed Implementation Plan (Phased)

### Phase 0: Preparation & Base Setup

| Task Name | Description |
|-----------|-------------|
| Create Monorepo Structure | Create Git monorepo structure (`backend-services/`, `frontend-next/`, `infra/`) |
| Docker Dev Environment Setup | Docker‑Compose dev environment: PostgreSQL×N, Kafka, RabbitMQ, Zookeeper |
| Initialize Next.js Project | Initialize Next.js project (TypeScript, Tailwind CSS, shadcn/ui) |
| Database Schema Design | Design database-per-service schemas; create **all** init SQL scripts upfront to avoid bottlenecks |
| Define Event Schemas | Define all event schemas (Avro/JSON), publish to schema registry, and create shared DTO stubs |
| Build Reference Skeleton Service | Build a **reference skeleton service** (Spring MVC, JPA, Spring Cloud Stream) to serve as a template for other agents |

---

### Phase 1: Backend Microservice Extraction

#### Sprint 1: Customer Service

| Task Name | Description |
|-----------|-------------|
| Extract Customer Domain | Extract Customer entity, JPA repository, service, MVC controller |
| Apply Customer DB Scripts | Apply `customer_db` init scripts (created in P0-4) |
| Implement Customer CRUD API | Implement Customer CRUD REST API with validation |
| Add Customer Messaging Binders | Add Spring Cloud Stream Kafka binder for events; RabbitMQ binder for RPC calls (use shared stubs) |
| Publish Customer Domain Events | Publish domain events: `CustomerCreated`, `CustomerUpdated` to `customer.events` |
| Implement Customer Saga Consumers | Implement saga consumers: listen to `EstimationRequested` → validate customer → publish `CustomerValidated`/`CustomerInvalidated` |

#### Sprint 2: Insurance Service

| Task Name | Description |
|-----------|-------------|
| Extract Insurance Domain | Extract Insurance, InsuranceType, InsuranceCompany entities + JPA repos |
| Apply Insurance DB Scripts | Apply `insurance_db` init scripts |
| Insurance CRUD APIs | CRUD APIs for insurance products, types, companies |
| Insurance Messaging Integration | Messaging integration: publish `InsuranceCreated`, `InsuranceUpdated` |
| Implement Insurance Saga Consumer | Saga consumer: listen for `CustomerValidated` AND `VehicleValidated` → calculate premium → publish `PremiumCalculated`/`CalculationFailed` |

#### Sprint 3: Estimation Service (SAGA Core)

| Task Name | Description |
|-----------|-------------|
| Extract Estimation Domain | Extract Estimation entity + calculation logic → JPA repository, service, MVC controller |
| Apply Estimation DB Scripts | Apply `estimation_db` init scripts |
| Estimation CRUD API | CRUD API for estimations (create, read, list) |
| Implement SAGA Choreography Handlers | Implement **SAGA choreography handlers**: event listeners for all relevant outcomes, manage saga state machine in DB (using `saga_id` column) |
| Implement SAGA Compensation Logic | Implement compensation logic: publish `EstimationFailed`, handle timeout, trigger compensating actions in other services |
| Estimation Messaging Integration | Messaging integration: publish `EstimationRequested`, consume terminal events, idempotency check |

#### Sprint 4: Vehicle & RealEstate Services

| Task Name | Description |
|-----------|-------------|
| Extract Vehicle Service | Vehicle Service: extract Vehicle + Car + related entities, JPA, CRUD API |
| Apply Vehicle DB Scripts | Apply `vehicle_db` init scripts |
| Extract RealEstate Service | RealEstate Service: extract RealEstate + related entities, JPA, CRUD API |
| Apply RealEstate DB Scripts | Apply `realestate_db` init scripts |
| Vehicle & RealEstate Event Integration | Both services: domain event publishing + saga consumers (`EstimationRequested` → validate → `VehicleValidated`/`RealEstateValidated`) |

#### Sprint 5: Reference Data Service

| Task Name | Description |
|-----------|-------------|
| Extract Reference Data Domain | Extract City, Profession, and other reference entities into dedicated service with JPA |
| Apply Reference DB Scripts | Apply `reference_db` init scripts |
| Implement Reference Data REST API | Implement REST endpoints for reference data, backed by Redis cache |
| Implement Reference Data RPC Listener | Implement RabbitMQ RPC listener for synchronous reference data queries from other services |

---

### Phase 2: Message Queue & Event-Driven Integration

| Task Name | Description |
|-----------|-------------|
| Finalize All Event Schemas | Finalize event schemas with all saga participants; update schema registry |
| Provision Message Infrastructure | Infrastructure as Code: Kafka topics (partitions, replication), RabbitMQ queues, exchanges, DLQ setup |
| Build Common Message Library | Build shared `common-message` library: event POJOs, serializers, abstract `MessagePublisher`/`MessageListener` classes |
| End-to-End SAGA Tests | End‑to‑end SAGA test: happy path, idempotency, timeout, DLQ handling |
| Implement Dead Letter Queue Handling | Implement Dead Letter Queue (DLQ) with retry mechanism (exponential backoff, max retries) |
| Setup Distributed Tracing | Distributed tracing with Spring Cloud Sleuth + Zipkin (propagate trace headers via Kafka/RabbitMQ) |

---

### Phase 3: Frontend Next.js Refactoring

#### Sprint 6: Base Architecture

| Task Name | Description |
|-----------|-------------|
| Configure Next.js Foundation | Configure Next.js App Router, Tailwind CSS, shadcn/ui |
| Build Layout Components | Build layout components (Header, Sidebar, Footer) |
| Setup State Management | Setup Zustand (client state) + React Query (server state) |
| Create API Client Layer | Create API client layer (calls BFF routes, attaches JWT automatically) |

#### Sprint 7: Feature Page Migration

| Task Name | Description |
|-----------|-------------|
| Build Customer Management Pages | Customer management pages (list, detail, create, edit, search) |
| Build Insurance Management Pages | Insurance management pages (products, types, companies) |
| Build Estimation Management Pages | Estimation management pages (create quote, view results, history) |
| Build Vehicle Management Pages | Vehicle management pages |
| Build Real Estate Management Pages | Real estate management pages |

#### Sprint 8: Advanced Frontend Features

| Task Name | Description |
|-----------|-------------|
| Implement SSR Data Fetching | Implement SSR data fetching patterns using Server Components → BFF → Gateway |
| Add Real-time Notifications | Real‑time notifications (WebSocket connection to Gateway or dedicated push service) |
| Implement Form Validation | Form validation with Zod + React Hook Form |
| Build Advanced Data Tables | Data tables with TanStack Table (pagination, sorting, filtering) |
| Implement Authentication | Authentication using NextAuth.js (connect to Auth Service) |

---

### Phase 4: API Gateway & Service Discovery

| Task Name | Description |
|-----------|-------------|
| Setup API Gateway Service | Set up Spring Cloud Gateway service |
| Configure Service Discovery | Configure service discovery (Eureka or Consul) |
| Define API Routing Rules | Define routing rules: `/api/customers/** → customer-service`, `/api/insurances/** → insurance-service`, etc. |
| Implement JWT Verification Filter | JWT verification filter (validate token, extract roles, forward user context) |
| Configure Rate Limiting | Rate limiting per IP/user |
| Configure Security & Limits | CORS configuration, request size limits, connection timeouts |

---

### Phase 5: Testing & Deployment

| Task Name | Description |
|-----------|-------------|
| Write Service Unit Tests | Unit tests for each service (JUnit 5, Mockito) |
| Write Integration Tests | Integration tests using Testcontainers (PostgreSQL, Kafka, RabbitMQ) |
| Write Contract Tests | Contract tests (Pact) between services |
| Setup CI/CD Pipelines | CI/CD pipelines (GitHub Actions) for build, test, Docker image push |
| Create Kubernetes Manifests | Kubernetes deployment manifests (Deployments, Services, ConfigMaps, Secrets) |
| Execute Performance Tests | Performance tests (JMeter/Gatling) executed through the API Gateway |

---

## V. Key Decision Points

1. **Imperative Stack:** All new microservices use Spring MVC + Spring Data JPA (Hibernate) + HikariCP connection pool. Avoids reactive learning curve; simplifies development.
2. **SAGA Choreography:** Explicit `sagaId`, idempotent consumers, timeout‑driven compensation, no central orchestrator.
3. **Database per Service:** Strict data isolation. Reference data may be cached locally (Redis or in‑memory TTL) to avoid cross‑service joins.
4. **Message Split:** Kafka for async events (saga, domain, analytics); RabbitMQ for sync RPC (reference lookups) and dead‑letter handling.
5. **Frontend Architecture:** Next.js App Router with BFF pattern. Always communicates via API Gateway. Legacy Vue app remains available until full migration.
6. **Shared Library:** All services use a common `common-message` module for event definitions, publishers, and consumers, ensuring consistency.
7. **No Direct Service‑to‑Service Calls:** All interservice communication goes through the message brokers or the Gateway.
8. **Correlation & Tracing:** Every HTTP request and event carries `traceId` and `sagaId` (when applicable). API Gateway injects `traceId`.
9. **Idempotency:** Every consumer deduplicates events by `sagaId` + event type.
10. **Externalized Configuration:** DB URLs, broker addresses, etc., are managed via Spring Cloud Config / Kubernetes ConfigMaps—no hardcoded values.

---

## VI. Agent Instruction Template

When assigning a task to an AI agent, use the following template to ensure consistency:
```markdown
Task: {Task Name} - {Task Description}
Branch: feature/{task-name-as-slug}
Acceptance Criteria:
  1. {Criterion 1}
  2. {Criterion 2}
  3. {Criterion 3}
Constraints:
  - Use Spring MVC + JPA/Hibernate (no reactive code)
  - All events must use the shared `common-message` library
  - Every event consumer must be idempotent (deduplicate by sagaId + event type)
  - Structured JSON logging with traceId, sagaId, eventId
  - External configuration via ConfigMaps / Spring Cloud Config (no hardcoded values)
  - Unit test coverage ≥ 80%
Reference Docs: {Link to schema registry, shared library documentation}
Deliverables:
  - Source code in the specified branch
  - Updated README.md (how to run, test, and configure the service)
  - Pull request to the `develop` branch
```
