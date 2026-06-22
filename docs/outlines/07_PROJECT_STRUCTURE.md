# Project Structure Outline

## Overview

The repository contains both legacy and target codebases. The legacy stack is preserved as-is during incremental migration.

---

## Directory Layout

```
InsuranceManagementSystem/
├── backend/                          # Legacy monolith (Java Spring Boot WebFlux)
├── frontend/                         # Legacy Vue 3 + Vite + TypeScript + TailwindCSS 4
├── services/                         # Target microservices (under construction)
│   ├── customer-service/             # Customer CRUD, search
│   ├── insurance-service/            # Insurance products, types, companies
│   ├── estimation-service/           # Insurance estimation/quote, SAGA coordination
│   ├── vehicle-service/              # Vehicle information management
│   ├── realestate-service/           # Real estate information management
│   ├── reference-data-service/       # Reference data (cities, professions, lookups)
│   ├── api-gateway/                  # Spring Cloud Gateway, routing, auth, rate limiting
│   ├── auth-service/                 # Authentication & JWT issuance/validation
│   └── reference-skeleton/           # Reference/template Spring Boot service (CRUD, Kafka, RabbitMQ)
├── frontend-next/                    # Target Next.js SSR (App Router) + Tailwind CSS + shadcn/ui
├── common/                           # Shared libraries
│   ├── common-message/               # Event schemas (SAGA + domain events), serialization, constants
│   └── common-test/                  # Shared test utilities
├── infra/                            # Infrastructure artifacts
│   ├── docker/                       # Docker Compose, .env, override configs
│   ├── sql/                          # Database init scripts per service (mounted by Docker Compose)
│   └── k8s/                          # Kubernetes manifests (future)
├── docs/                             # Project documentation (outlines, stories, tasks, plans)
├── settings.gradle.kts               # Unified Gradle multi-project build (all services + common modules)
└── .env.template                     # Placeholder template for environment variables
```

---

## Technology Stack Per Layer

| Layer | Legacy | Target |
|-------|--------|--------|
| Backend | Java Spring Boot WebFlux (monolith) | Java 25, Spring Boot MVC, Spring Data JPA (Hibernate) |
| Frontend | Vue 3 + Vite + TypeScript + TailwindCSS 4 | Next.js 15+ (App Router, SSR), TypeScript, Tailwind CSS |
| UI Library | TailwindCSS (raw) | shadcn/ui (Radix primitives + Tailwind CSS) |
| Database | MS SQL Server (R2DBC) | PostgreSQL 16+ (database per service) |
| Messaging | — | Kafka (SAGA/events), RabbitMQ (RPC) |
| API Gateway | — | Spring Cloud Gateway |
| Auth | Dummy (any credentials accepted) | Dedicated Auth Service (JWT) |
| State Mgmt | Pinia | Zustand + React Query |
| Build | Gradle (backend), Vite (frontend) | Gradle (all services), Next.js (frontend-next) |

---

## Target Service Conventions

All target services under `services/` share:

- **Spring Boot MVC + Spring Data JPA (Hibernate)** + **PostgreSQL**
- Each service has its own dedicated database (Database per Service pattern)
- Package root: `com.insurancemanagementsystem.<service-name>/`
- See [`01_SYSTEM_ARCHITECTURE.md`](./01_SYSTEM_ARCHITECTURE.md) for full architectural rules

---

## Build Order (Dependency Chain)

Changes affecting multiple services must be implemented bottom-up (no circular dependencies):

```
Reference Data → Auth → Customer/Vehicle/RealEstate → Insurance → Estimation → API Gateway
```

- **Frontend-next** depends only on API Gateway contracts
- **common-message** is a dependency of all services — build it first
- **reference-skeleton** is a template — not in the dependency chain

---

## Legacy Preservation Rule

The legacy monolith `backend/` and Vue `frontend/` are **preserved as-is** during incremental migration. Never modify the legacy stack unless explicitly directed for maintenance.
