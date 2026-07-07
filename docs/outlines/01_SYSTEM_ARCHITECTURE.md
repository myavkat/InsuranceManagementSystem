# System Architecture Outline

## Overview

Migration from a monolithic Java Spring Boot WebFlux + Vue 3 application to a **domain-driven microservice architecture** with **SAGA choreography** and a **Next.js SSR** frontend.

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Backend (microservices) | Java 25, Spring Boot MVC, Spring Data JPA (Hibernate) |
| Frontend (target) | Next.js 15+ (App Router, SSR), TypeScript, Tailwind CSS |
| UI Component Library | shadcn/ui (Radix primitives + Tailwind CSS) |
| Database | PostgreSQL 16+ (database per service) |
| Message Brokers | Kafka (SAGA/events, domain events, RPC) |
| API Gateway | Spring Cloud Gateway |
| Auth | Dedicated Auth Service (JWT issuance + validation) |
| Build Tool | Gradle (standardized across all services) |
| Containerization | Docker Compose (local dev) |

---

## Microservice Breakdown

| Service | Responsibility | Database |
|---------|---------------|----------|
| **Auth Service** | User registration, login, JWT issuance/validation | `auth_db` |
| **Customer Service** | Customer CRUD, search, validation | `customer_db` |
| **Vehicle Service** | Vehicle info management (car, brand, model, engine, fuel, type, package) | `vehicle_db` |
| **RealEstate Service** | Real estate info management (construction type, luxury class, usage type) | `realestate_db` |
| **Insurance Service** | Insurance products, types, company management | `insurance_db` |
| **Estimation Service** | Insurance estimation/quote, SAGA coordination, timeout enforcement | `estimation_db` |
| **Reference Data Service** | Cities, professions, lookup tables | `reference_data_db` |
| **API Gateway** | Routing, auth validation, rate limiting | — |

---

## Communication Architecture

```
Client (Next.js SSR)
      │
      ▼
API Gateway (Spring Cloud Gateway)
      │
      ├──► Auth Service (JWT)
      ├──► Customer Service
      ├──► Vehicle Service
      ├──► RealEstate Service
      ├──► Insurance Service
      ├──► Estimation Service
      └──► Reference Data Service

Inter-service: Kafka (SAGA events, domain events, RPC)
     No direct REST between services.
```

---

## Key Architectural Rules

1. **No service calls another's REST API directly** — all inter-service communication via Kafka.
2. **Database per service** — no shared databases, no cross-service joins.
3. **SAGA choreography** — no central orchestrator; services react to events and publish outcomes.
4. **Idempotent consumers** — deduplicate events using `sagaId` + event type.
5. **API Gateway is the single entry point** — external requests route through Gateway; Frontend BFF calls Gateway only.
6. **Legacy preserved during migration** — `backend/` and `frontend/` remain operational on separate subdomain until fully replaced.
