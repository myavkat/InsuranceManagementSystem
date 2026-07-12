# Insurance Management System

A domain-driven microservice platform for insurance estimation and policy management, built with Spring Boot and Next.js.

---

## Architecture

Eight microservices plus service discovery, each owning its domain data and collaborating via asynchronous events:

| Service | Responsibility | Database | Status |
|---------|---------------|----------|--------|
| **API Gateway** | Routing, auth validation, rate limiting | — | Stub |
| **Auth Service** | User management, JWT issuance / validation | `auth_db` | Stub |
| **Customer Service** | Customer CRUD, SAGA validation | `customer_db` | Active |
| **Vehicle Service** | Vehicle info, brand/model/engine reference data | `vehicle_db` | Active |
| **RealEstate Service** | Real estate info, construction/luxury/usage reference | `realestate_db` | Active |
| **Insurance Service** | Insurance products, types, companies | `insurance_db` | Active |
| **Estimation Service** | Insurance estimation, SAGA coordination, timeout | `estimation_db` | Active |
| **Reference Data Service** | Cities, professions, lookup tables | `reference_data_db` | Active |

**Service Discovery:** Eureka Server on port 8761.

### Communication

- **External**: All requests route through the **API Gateway** (Spring Cloud Gateway). No direct service exposure.
- **Inter-service**: **Kafka** for all inter-service communication (SAGA events, domain events).
- **Pattern**: **Choreography-based SAGA** — no central orchestrator. Services react to events and publish outcomes.
- **Idempotency**: All consumers deduplicate by `sagaId` + event type via atomic INSERT with UNIQUE constraint.

### Estimation SAGA Flow

```
EstimationRequested → CustomerValidated + VehicleValidated + RealEstateValidated
                   → PremiumCalculated → WAITING_APPROVAL
                   → (accept-offer) → PAYMENT_WAITING
                   → (process-payment) → ACTIVE
```

Status lifecycle: `STARTED` → `WAITING_APPROVAL` → `PAYMENT_WAITING` → `ACTIVE` — with `FAILED` and timeout (5 min) compensation paths.

### Infrastructure

| Component | Technology | Port |
|-----------|-----------|------|
| Databases (8) | PostgreSQL 16 | 5432–5439 |
| Message Broker | Kafka (KRaft mode) | 9092 |
| Distributed Tracing | Zipkin | 9411 |
| Rate Limiting | Redis 7 Alpine | 6379 |
| Service Discovery | Eureka | 8761 |

---

## Frontend

**Next.js 16** (App Router, SSR) with **React 19**, **TypeScript**, **Tailwind CSS 4**, and **shadcn/ui** (Base UI React, style: "base-nova").

Server Components by default with explicit `"use client"` boundaries. Client state via **Zustand**, server state via **TanStack React Query**, forms via **React Hook Form** + **Zod**, tables via **TanStack React Table**.

### Pages

- **Dashboard** — overview with stats cards
- **Customers** — list, create, edit, detail view
- **Vehicles** — list, create, edit (brand/model cascading)
- **Real Estate** — list, create, edit
- **Insurances** — products, types, companies
- **Offers** (Estimations) — list with status filters, create, detail with status-driven action button, payment page

---

## Prerequisites

- **Java 25**
- **Docker Compose**
- **Node.js 20+**
- Gradle (included via wrapper — `gradlew.bat`)

---

## Getting Started

### One-Command Startup

```cmd
start-all.cmd
```

Builds all JARs via `./gradlew.bat bootJar -x test`, then starts infrastructure + all 8 services as Docker containers. Use `-skip-build` to skip the Gradle build step.

```cmd
start-all.cmd -skip-build
```

Stop everything:

```cmd
stop-all.cmd
```

### Step-by-Step Startup

```bash
# 1. Start infrastructure (PostgreSQL × 8, Kafka, Redis, Zipkin, Eureka)
cd infra/docker
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d

# 2. Build all service JARs
cd ../..
./gradlew.bat bootJar -x test

# 3. Start all microservices
cd infra/docker
docker compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.services.yml up -d --build

# 4. Start frontend
cd ../../frontend
npm install
npm run dev
```

**Browse to** `http://localhost:3000`.

---

## Service Ports

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Customer Service | 8081 |
| Vehicle Service | 8082 |
| RealEstate Service | 8083 |
| Insurance Service | 8084 |
| Estimation Service | 8085 |
| Reference Data Service | 8086 |
| Auth Service | 8087 |
| Next.js Frontend | 3000 |
| Eureka Dashboard | 8761 |

---

## Build & Test

```bash
# Build a specific microservice
./gradlew.bat :services:<service-name>:build

# Run tests for a specific microservice
./gradlew.bat :services:<service-name>:test

# Build all JARs (skip tests)
./gradlew.bat bootJar -x test

# Frontend
cd frontend
npm run build
npm run lint
npm run dev
```

---

## Project Layout

```
├── common/                       # Shared libraries
│   ├── common-message/           # Kafka event POJOs, EventType constants
│   ├── common-web/               # Shared web config (security, filters)
│   └── common-test/              # Shared test utilities
├── services/                     # Microservices (Gradle submodules)
│   ├── api-gateway/
│   ├── auth-service/
│   ├── customer-service/
│   ├── vehicle-service/
│   ├── realestate-service/
│   ├── insurance-service/
│   ├── estimation-service/
│   ├── reference-data-service/
│   └── eureka-server/
├── frontend/                # Next.js 16 SSR application
│   └── src/
│       ├── app/                  # App Router (pages, layouts, API routes)
│       ├── components/           # shadcn/ui + feature components
│       ├── hooks/                # Custom React hooks
│       └── lib/                  # API client, Zustand stores, utilities
├── infra/
│   ├── docker/                   # Docker Compose files, Dockerfiles
│   ├── kafka/                    # Kafka topic initialization script
│   ├── k8s/                      # Kubernetes manifests (planned)
│   └── sql/                      # Database init scripts (per service)
└── docs/
    ├── outlines/                 # Architecture blueprint (12 files)
    ├── stories/                  # User stories (7 files)
    ├── plans/                    # Execution checklists (active + archived)
    └── tasks/                    # Task templates
```

---

## Documentation

The `docs/` directory implements a structured engineering framework:

| Directory | Purpose |
|-----------|---------|
| [`outlines/`](docs/outlines/) | Permanent architectural decisions and conventions — treated as immutable law |
| [`stories/`](docs/stories/) | User-facing feature requirements with acceptance criteria |
| [`plans/`](docs/plans/) | Step-by-step execution checklists — the active source of truth for development |
| [`tasks/`](docs/tasks/) | Directed instruction templates linking outlines and stories |

Key outlines:

- [`01_SYSTEM_ARCHITECTURE.md`](docs/outlines/01_SYSTEM_ARCHITECTURE.md) — Tech stack, microservice breakdown, communication architecture
- [`02_MICROSERVICES_SPECIFICATIONS.md`](docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md) — Per-service entities, endpoints, SAGA consumers
- [`03_SAGA_PATTERN.md`](docs/outlines/03_SAGA_PATTERN.md) — SAGA choreography flow, event catalog, idempotency, timeout, compensation
- [`04_MESSAGE_QUEUE_TOPOLOGY.md`](docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md) — Kafka topics, partitions, retention, consumer configuration
- [`05_NEXTJS_FRONTEND.md`](docs/outlines/05_NEXTJS_FRONTEND.md) — Next.js App Router architecture, component structure, data flow
- [`12_DEVELOPER_COMMANDS.md`](docs/outlines/12_DEVELOPER_COMMANDS.md) — Build, run, test commands for all subsystems

---

## Features (TBA)

| Feature | Status | Notes |
|---------|--------|-------|
| **Auth Service** | Planned | User registration, login, JWT issuance/validation, BCrypt, account lockout |
| **API Gateway** | Planned | Route-level rate limiting via Redis, JWT auth filter chain, response wrapper |
| **Kubernetes Deployment** | Planned | `infra/k8s/` directory created; manifests and Helm charts pending |
| **CI/CD Pipelines** | Planned | No GitHub Actions workflows configured yet |
| **Test Coverage (JaCoCo)** | Planned | 80% coverage target for insurance-service and estimation-service |
| **Frontend Auth Integration** | Planned | Login/register pages exist; JWT cookie integration with gateway pending |
| **OpenAPI / Swagger UI** | Planned | Springdoc integration for API documentation |

---

## Environment Variables

All configurable variables are documented in [`.env.template`](.env.template) (single source of truth). Copy it to configure your deployment:

```bash
cp .env.template .env
```

---

## License

MIT
