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
```

---

## Microservices (Gradle)

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
# Launch a single service (requires infra running)
.\gradlew.bat :services:<service-name>:bootRun
```

### Run All Services (One-Command Docker Startup)

The recommended way to run everything at once. Builds all JARs, starts infrastructure
(PostgreSQL × 8, Kafka, Zipkin, Redis), and launches all active services as containers.

```bash
# From repo root:
start-all.cmd                 # full build + infra + services
start-all.cmd -skip-build     # skip Gradle build (JARs already fresh)

# Stop everything:
stop-all.cmd

# Or manually (without the wrapper script):
.\gradlew.bat bootJar -x test && docker compose \
  -f infra/docker/docker-compose.yml \
  -f infra/docker/docker-compose.override.yml \
  -f infra/docker/docker-compose.services.yml \
  up -d --build
```

Under the hood:
- `infra/docker/Dockerfile.service` — shared runtime image, copies a pre-built JAR into `eclipse-temurin:25-jre-alpine`
- `infra/docker/docker-compose.services.yml` — defines all 6 services on `insurance-net` with health checks and `SERVER_PORT=8080`
- Kafka uses an `INTERNAL://kafka:9094` listener so containers can reach the broker via Docker network DNS; services bind to it via `KAFKA_BOOTSTRAP_SERVERS=kafka:9094`

| Service | Host Port | Container Port |
|---------|-----------|----------------|
| customer-service | 8081 | 8080 |
| vehicle-service | 8082 | 8080 |
| realestate-service | 8083 | 8080 |
| insurance-service | 8084 | 8080 |
| estimation-service | 8085 | 8080 |
| reference-data-service | 8086 | 8080 |

### Service Names

Replace `<service-name>` with one of the active services:
`customer-service`, `insurance-service`, `estimation-service`, `vehicle-service`,
`realestate-service`, `reference-data-service`

Stub/template services (not yet in the active build):
`api-gateway`, `auth-service`, `reference-skeleton`

### Common Module

```bash
.\gradlew.bat :common:common-message:build
.\gradlew.bat :common:common-test:build
```

---

## Infrastructure (Docker Compose)

All commands run from the repo root.

```bash
# Start all services (PostgreSQL, Kafka)
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

> **Note:** Kafka topics are created automatically by the `kafka-init` container on `docker compose up`. See `infra/kafka/create-topics.sh` for the topic configuration.

---

## Order of Operations (Fresh Start)

### Quick: all-in-one Docker

```bash
start-all.cmd
```
That's it. All infra + all 6 services come up. Use `stop-all.cmd` to shut down.

### Step-by-step (for single-service development)

1. **Start infra:** `docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml up -d`
2. **Build common:** `.\gradlew.bat :common:common-message:build`
3. **Build & run a service:** `.\gradlew.bat :services:<service-name>:bootRun`
4. **Start frontend:** `cd frontend-next && npm run dev`
