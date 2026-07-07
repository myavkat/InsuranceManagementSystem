# Insurance Management System

A domain-driven microservice platform for insurance estimation and policy management, built with Spring Boot and Next.js.

---

## Architecture

Eight microservices, each owning its domain data and collaborating via asynchronous events:

| Service | Responsibility | Database |
|---------|---------------|----------|
| **Auth Service** | User management, JWT issuance / validation | `auth_db` |
| **Customer Service** | Customer CRUD, SAGA validation | `customer_db` |
| **Vehicle Service** | Vehicle info, brand/model reference data | `vehicle_db` |
| **RealEstate Service** | Real estate info, construction/luxury/usage reference | `realestate_db` |
| **Insurance Service** | Insurance products, types, companies | `insurance_db` |
| **Estimation Service** | Insurance estimation, SAGA coordination, timeout | `estimation_db` |
| **Reference Data Service** | Cities, professions, lookup tables | `reference_data_db` |
| **API Gateway** | Routing, auth validation, rate limiting | — |

### Communication

- **External**: All requests route through the **API Gateway** (Spring Cloud Gateway). No direct service exposure.
- **Inter-service**: **Kafka** for all inter-service communication (SAGA events, domain events, RPC-style lookups).
- **Pattern**: **Choreography-based SAGA** — no central orchestrator. Services react to events and publish outcomes.
- **Idempotency**: All consumers deduplicate by `sagaId` + event type.

### Frontend

**Next.js 15** (App Router, SSR) with **Tailwind CSS** and **shadcn/ui**.  
Server Components render by default; client components (forms, interactivity) use React Query for data fetching.  
Client state managed via **Zustand**; auth state persisted as HTTP-only cookie.

---

## Prerequisites

- Java 25
- Docker Compose
- Node.js 20+
- Gradle (included per-service via wrapper)

---

## Getting Started

```bash
# 1. Start infrastructure (PostgreSQL, Kafka)
docker compose up -d

# 2. Start microservices (each in its own terminal)
for svc in auth-service customer-service vehicle-service realestate-service \
           insurance-service estimation-service reference-data-service api-gateway; do
  cd services/$svc && ./gradlew bootRun
done

# 3. Start frontend
cd frontend-next
npm install
npm run dev
```

**Browse to** `http://localhost:3000`.

---

## Service Ports

| Service | Port |
|---------|------|
| API Gateway | 8080 |
| Auth Service | 8081 |
| Customer Service | 8082 |
| Vehicle Service | 8083 |
| RealEstate Service | 8084 |
| Insurance Service | 8085 |
| Estimation Service | 8086 |
| Reference Data Service | 8087 |
| Next.js Frontend | 3000 |

---

## Build & Test

```bash
# Build and test a microservice
cd services/<service-name>
./gradlew clean build
./gradlew test

# Frontend (lint, type-check, unit tests)
cd frontend-next
npm run build
npm run lint
npm run test

# Run all infra
docker compose up -d
docker compose down
```

---

## Project Layout

```
├── services/                    # Microservices
│   ├── auth-service/
│   ├── customer-service/
│   ├── vehicle-service/
│   ├── realestate-service/
│   ├── insurance-service/
│   ├── estimation-service/
│   ├── reference-data-service/
│   └── api-gateway/
├── frontend-next/               # Next.js SSR application
│   ├── app/                     # App Router (pages + BFF)
│   ├── components/              # shadcn/ui + feature components
│   └── lib/                     # API client, Zustand stores
├── backend/                     # (legacy — preserved for reference)
├── frontend/                    # (legacy — preserved for reference)
└── docs/
    ├── outlines/                # Architecture blueprint
    ├── stories/                 # User stories
    ├── plans/                   # Execution checklists
    └── DESIGN_PLAN.md           # Migration design document
```

---

## License

MIT
