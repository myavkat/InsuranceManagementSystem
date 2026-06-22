# Developer Commands Outline

Quick-reference for building, running, and testing each subsystem.

---

## Frontend (Next.js)

All commands run from `frontend-next/`:

```bash
cd frontend-next
npm run dev                  # Next.js dev server at localhost:3000
npm run build                # Production build (SSR + static generation)
npm run start                # Production server
npm run lint                 # ESLint
npm run test                 # Jest / Vitest
```

---

## Target Microservices (Gradle)

All commands run from the repo root. Use `.\gradlew.bat` on Windows, `./gradlew` on Unix.

### Build & Test

```bash
# Build a single service (compile + test + package)
.\gradlew.bat :services:<service-name>:build

# Run tests only
.\gradlew.bat :services:<service-name>:test

# Build everything (all services + common modules)
.\gradlew.bat build
```

### Run (Dev Server)

```bash
# Requires local PostgreSQL and Kafka/RabbitMQ (see infra section)
.\gradlew.bat :services:<service-name>:bootRun
```

### Service Names

Replace `<service-name>` with one of:
`customer-service`, `insurance-service`, `estimation-service`, `vehicle-service`,
`realestate-service`, `reference-data-service`, `api-gateway`, `auth-service`, `reference-skeleton`

### Common Module

```bash
.\gradlew.bat :common:common-message:build
.\gradlew.bat :common:common-test:build
```

---

## Infrastructure (Docker Compose)

All commands run from the repo root.

```bash
# Start all services (PostgreSQL, Kafka, RabbitMQ)
docker compose -f infra/docker/docker-compose.yml up -d

# Start with local dev overrides
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml up -d

# Stop all services
docker compose -f infra/docker/docker-compose.yml down

# Start a specific database only
docker compose -f infra/docker/docker-compose.yml up -d customer-db
```

### Default Ports

| Service | Port |
|---------|------|
| PostgreSQL | `5432` |
| Kafka | `9092` |
| RabbitMQ | `5672` |
| RabbitMQ Management UI | `15672` |

---

## Order of Operations (Fresh Start)

1. **Start infra:** `docker compose up -d` (PostgreSQL, Kafka, RabbitMQ)
2. **Build common:** `.\gradlew.bat :common:common-message:build`
3. **Build & run a service:** `.\gradlew.bat :services:<service-name>:bootRun`
4. **Start frontend:** `cd frontend-next && npm run dev`
