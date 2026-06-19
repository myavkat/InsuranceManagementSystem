# From Monolithic Spring CRUD + Vue 3 to Microservices with SAGA (RabbitMQ/Kafka) and Next.js SSR

---

## I. Existing System Analysis

### 1.1 Tech Stack
| Layer | Existing Technology | Target Technology |
|-------|---------------------|-------------------|
| Backend | Java Spring Boot WebFlux (reactive) | Java Spring Boot MVC + JPA/Hibernate (imperative) per microservice |
| Frontend | Vue 3 + Vite + TypeScript | Next.js (SSR) + Tailwind CSS |
| Build Tool | Gradle | Gradle (standardized) |

### 1.2 Data Models (Entities)
Core entities identified in the monolith:
- Customer
- Insurance, InsuranceType, InsuranceCompany
- Estimation
- Vehicle, Car + related: CarBrand, CarModel, CarEngine, CarFuelType, CarType, CarPackage
- RealEstate + related: RealEstateConstructionType, RealEstateLuxuryClass, RealEstateUsageType
- Profession, City

### 1.3 Existing API Endpoints (Controllers)
- CustomerController
- EstimationController
- InsuranceController
- InsuranceTypeController
- ProfessionController
- RealEstateController
- VehicleController
- HomeController

### 1.4 Frontend Structure
- Vue 3 + Vite + TypeScript
- Directories: components, views, services, stores, router, composables
- State management: Pinia; API service layer present

---

## II. Target Architecture Design

### 2.1 Microservice Breakdown (Domain-Driven)

| Microservice | Responsibility | Original Monolith Mapping |
|--------------|----------------|---------------------------|
| **Customer Service** | Customer CRUD, search | CustomerController + Customer entity |
| **Insurance Service** | Insurance products, types, company management | InsuranceController, InsuranceTypeController + related entities |
| **Estimation Service** | Insurance estimation/quote calculation, SAGA coordination | EstimationController + Estimation entity |
| **Vehicle Service** | Vehicle information management | VehicleController + Car + related entities |
| **RealEstate Service** | Real estate information management | RealEstateController + related entities |
| **Reference Data Service** | Reference data (cities, professions, lookup tables) | ProfessionController + City + others |
| **API Gateway** | Routing, authentication, rate limiting | New |
| **Auth Service** | Authentication & JWT issuance/validation | New (existing only has dummy auth) |

All new microservices use **Spring Boot (MVC)** + **Spring Data JPA (Hibernate)** + **PostgreSQL**. Each service has its own dedicated database (Database per Service).

---

### 2.2 SAGA Pattern – Choreography

Adopt the **Choreography** style SAGA. Services collaborate exclusively via events; no central orchestrator.

**Global Rules:**
- Every saga starts with the Estimation Service generating a unique `sagaId`.
- All subsequent events **must** carry the same `sagaId` for correlation.
- Each service listens for specific events, performs its local transaction, and publishes the outcome.
- Compensation: if any step fails, a compensating event `EstimationFailed` triggers rollback logic in all participating services.
- The Estimation Service implements a timeout – if no terminal event arrives within a configurable window, it publishes `EstimationFailed`.

**Detailed SAGA Flow – Create Insurance Estimation:**

1. **Estimation Service** receives request → creates pending `Estimation` (status `STARTED`) → publishes `EstimationRequested` to Kafka topic `estimation.saga` (key = `sagaId`).
2. **Customer Service** consumes `EstimationRequested` → validates customer existence → publishes `CustomerValidated` or `CustomerInvalidated` to `estimation.saga`.
3. **Vehicle Service** consumes `EstimationRequested` → validates vehicle → publishes `VehicleValidated` or `VehicleInvalidated`.
4. **Insurance Service** listens for both `CustomerValidated` and `VehicleValidated` (correlated by `sagaId`). Once both arrive, it calculates the premium → publishes `PremiumCalculated` or `CalculationFailed`.
5. **Estimation Service** consumes `PremiumCalculated` → updates estimation status to `COMPLETED` with premium details.  
   If any failure event (`*Invalidated`, `CalculationFailed`) arrives → estimation is set to `REJECTED` and `EstimationFailed` is published.
6. **Compensation:** Any service that performed a reversible action must listen for `EstimationFailed` and undo its changes (e.g., release a temporary reservation).

**Cross-cutting Concerns:**
- **Idempotency:** Consumers deduplicate events using `sagaId` + event type (store in a local table or in‑memory with TTL).
- **Event Persistence:** Kafka stores all events (log‑compacted topics for entity state, standard topics for saga).
- **Correlation & Tracing:** Each event carries `traceId` (from distributed tracing) and `sagaId`; services log these in structured JSON.

---

### 2.3 Message Queue Division of Labor

| Component | Usage | Topics / Queues |
|-----------|-------|-----------------|
| **Kafka** | SAGA events, domain events (audit, analytics, eventual consistency) | `estimation.saga`, `customer.events`, `insurance.events`, `vehicle.events`, `realestate.events`, `reference-data.events` |
| **RabbitMQ** | Synchronous RPC calls (e.g., fetch reference data), dead‑letter handling | `rpc.reference-data`, `dlq.saga` |

**Rule:** No service calls another’s REST API directly. All interservice communication uses the message brokers or the API Gateway.

---

### 2.4 Next.js Frontend Architecture

```
frontend-next/
├── app/                    # App Router (SSR)
│   ├── (auth)/            # Authentication pages (login, register)
│   ├── (dashboard)/       # Main dashboard layout
│   │   ├── customers/
│   │   ├── insurances/
│   │   ├── estimations/
│   │   └── vehicles/
│   ├── api/               # BFF layer (calls API Gateway)
│   └── layout.tsx
├── components/
│   ├── ui/                # shadcn/ui components
│   └── features/          # Feature-specific components
├── lib/
│   ├── api/               # API client (base URL = API Gateway)
│   └── store/             # Zustand (client state) + React Query 
(server state)
└── tailwind.config.js
```

**Key Notes:**
- The BFF (`app/api/*`) always calls the **API Gateway** (env var `GATEWAY_URL`).
- Server Components fetch data via the BFF; client components use React Query against the same BFF routes.
- The legacy Vue application remains operational on a separate subdomain during migration. The API Gateway routes traffic according to the `Host` header or a cookie, enabling a gradual switchover.
