# Project Structure Outline

## Overview

The repository is organized as a Gradle multi-module project with microservices, a shared library layer, a Next.js frontend, and infrastructure artifacts.

---

## Directory Layout

```
InsuranceManagementSystem/
├── services/                         # Microservices
│   ├── customer-service/             # Customer CRUD, search
│   ├── insurance-service/            # Insurance products, types, companies
│   ├── estimation-service/           # Insurance estimation/quote, SAGA coordination
│   ├── vehicle-service/              # Vehicle information management
│   ├── realestate-service/           # Real estate information management
│   ├── reference-data-service/       # Reference data (cities, professions, lookups)
│   ├── api-gateway/                  # Spring Cloud Gateway, routing, auth, rate limiting (stub)
│   ├── auth-service/                 # Authentication & JWT issuance/validation (stub)
│   └── reference-skeleton/           # Reference/template Spring Boot service (CRUD, Kafka)
├── frontend-next/                    # Next.js SSR (App Router) + Tailwind CSS + shadcn/ui
├── common/                           # Shared libraries
│   ├── common-message/               # Event schemas (SAGA + domain events), serialization, constants
│   ├── common-web/                   # Shared web config (Springdoc OpenAPI, exception handling, tracing)
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

> **Technology stack:** See [`01_SYSTEM_ARCHITECTURE.md`](./01_SYSTEM_ARCHITECTURE.md) for the full technology stack.
>
> **Service conventions:** See [`01_SYSTEM_ARCHITECTURE.md`](./01_SYSTEM_ARCHITECTURE.md) for architectural rules and [`02_MICROSERVICES_SPECIFICATIONS.md`](./02_MICROSERVICES_SPECIFICATIONS.md) for per-service specs.

---

## Build Order (Dependency Chain)

Changes affecting multiple services must be implemented bottom-up (no circular dependencies):

```
Common modules → Reference Data → Customer/Vehicle/RealEstate → Insurance → Estimation
```

- **Frontend-next** depends only on API Gateway contracts (once gateway is implemented)
- **common-message** is a dependency of all services — build it first
- **reference-skeleton** is a template — not in the dependency chain
- **auth-service** and **api-gateway** are stubs without build files — not yet in the build chain

---
