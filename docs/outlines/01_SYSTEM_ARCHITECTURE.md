# System Architecture Outline

## Overview

A **domain-driven microservice architecture** with **SAGA choreography**, an **API Gateway** entry point, and a **Next.js SSR** frontend.

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Backend (microservices) | Java 25, Spring Boot MVC, Spring Data JPA (Hibernate) |
| Frontend | Next.js 16 (App Router, SSR), TypeScript, Tailwind CSS |
| UI Component Library | shadcn/ui (Base UI React primitives + Tailwind CSS) |
| Database | PostgreSQL 16+ (database per service) |
| Message Brokers | Kafka (SAGA/events, domain events, RPC) |
| API Gateway | Spring Cloud Gateway |
| Auth | Dedicated Auth Service (JWT issuance + validation) |
| Build Tool | Gradle (standardized across all services) |
| Containerization | Docker Compose (local dev) |

---

## Microservice Breakdown

| Service | Responsibility | Database | Status |
|---------|---------------|----------|--------|
| **Auth Service** | User registration, login, JWT issuance/validation | `auth_db` | Stub (planned) |
| **Customer Service** | Customer CRUD, search, validation | `customer_db` | Active |
| **Vehicle Service** | Vehicle info management (car, brand, model, engine, fuel, type, package) | `vehicle_db` | Active |
| **RealEstate Service** | Real estate info management (construction type, luxury class, usage type) | `realestate_db` | Active |
| **Insurance Service** | Insurance products, types, company management | `insurance_db` | Active |
| **Estimation Service** | Insurance estimation/quote, SAGA coordination, timeout enforcement | `estimation_db` | Active |
| **Reference Data Service** | Cities, professions, lookup tables | `reference_data_db` | Active |
| **API Gateway** | Routing, auth validation, rate limiting | — | Stub (planned) |

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
