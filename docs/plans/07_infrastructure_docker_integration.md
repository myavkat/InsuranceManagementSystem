# Plan 07: Infrastructure & Docker Integration

> **Status:** Complete (code changes applied, JARs verified)
> **Branch:** `phase4-api-gateway`
> **Depends on:** Plan 01 (Eureka Server), Plan 02 (API Gateway Core)
> **Can run in parallel with:** Plans 03, 04, 05
> **Blocks:** End-to-end testing via Docker Compose

## Objective

Integrate the Eureka Server and API Gateway into the Docker Compose infrastructure:
- Add Eureka Server and API Gateway as Docker Compose services
- Add Eureka Server to `docker-compose.yml` (infrastructure container, like Redis/Zipkin)
- Add API Gateway to `docker-compose.services.yml` (application container, like customer-service)
- Update service configurations for Docker networking (Eureka URL, internal Kafka listener)
- Update `start-all.cmd` and `stop-all.cmd` scripts
- Update `.env.template` with any new environment variables

## Files to Read Before Starting

| File | Purpose |
|------|---------|
| `infra/docker/docker-compose.yml` | Infrastructure containers (DBs, Kafka, Redis, Zipkin) — add Eureka here |
| `infra/docker/docker-compose.override.yml` | Port mappings for local dev |
| `infra/docker/docker-compose.services.yml` | Service containers — add Gateway here |
| `infra/docker/Dockerfile.service` | Shared Dockerfile for service containers |
| `infra/docker/.env` | Docker environment variables |
| `services/customer-service/src/main/resources/application.yml` | Service config — reference for Docker env vars |
| `start-all.cmd` | Batch script that builds and starts everything |
| `stop-all.cmd` | Batch script that stops everything |
| `.env.template` | Master environment variable reference |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Port allocation table, Docker networking details |

## Technical Context (Inline)

### Port Allocation (from `13_ENVIRONMENT_QUIRKS.md`)

| Service | Host Port | Container Port |
|---------|-----------|----------------|
| Eureka Server | `8761` | `8761` (new) |
| API Gateway | `8080` | `8080` |
| customer-service | `8081` | `8080` |
| vehicle-service | `8082` | `8080` |
| realestate-service | `8083` | `8080` |
| insurance-service | `8084` | `8080` |
| estimation-service | `8085` | `8080` |
| reference-data-service | `8086` | `8080` |

### Docker Networking

All containers use the `insurance-net` bridge network. Internal communication uses container names as DNS hostnames:
- `kafka:9094` — Kafka internal listener
- `eureka-server:8761` — Eureka (new)
- `customer-db:5432`, `vehicle-db:5432`, etc. — databases

### Eureka Configuration in Docker

In the Docker environment, `EUREKA_SERVER_URL` should point to the container name:
```
EUREKA_SERVER_URL=http://eureka-server:8761/eureka/
```

Services access Eureka via `eureka-server` hostname (Docker DNS), while the host accesses it via `localhost:8761`.

### API Gateway Routing in Docker

The Gateway routes to `lb://<service-name>` which resolves via Eureka. When running in Docker:
- Each service registers with Eureka using its container hostname
- The Gateway resolves `lb://customer-service` to `http://<container-ip>:8080`
- `preferIpAddress: true` ensures Eureka registers the container IP, not hostname

### Existing Patterns (from `docker-compose.services.yml`)

Each service is defined with:
```yaml
service-name:
  build:
    context: ../../
    dockerfile: infra/docker/Dockerfile.service
    args:
      JAR_PATH: services/<name>/build/libs/<name>-0.0.1-SNAPSHOT.jar
  container_name: service-name
  deploy:
    resources:
      limits:
        memory: 1G
        cpus: "1.0"
  ports: ["HOST_PORT:8080"]
  environment:
    SERVER_PORT: "8080"
    SPRING_PROFILES_ACTIVE: dev
    KAFKA_BOOTSTRAP_SERVERS: kafka:9094
    ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
    SPRING_DATASOURCE_URL: jdbc:postgresql://<db-container>:5432/<db_name>
    OUTBOX_POLL_INTERVAL_MS: "5000"
    OPENAPI_SERVER_URL: http://localhost:<HOST_PORT>
    EUREKA_SERVER_URL: http://eureka-server:8761/eureka/       # NEW
  networks: [insurance-net]
  restart: unless-stopped
  depends_on:
    kafka: { condition: service_healthy }
    <service>-db: { condition: service_healthy }
    eureka-server: { condition: service_healthy }               # NEW
```

### Gateway Docker Compose Service

The Gateway is special — it doesn't have a database and doesn't connect to Kafka. Its dependencies are:
- Eureka Server (for service discovery)
- Redis (for rate limiting)

```yaml
api-gateway:
  build:
    context: ../../
    dockerfile: infra/docker/Dockerfile.service
    args:
      JAR_PATH: services/api-gateway/build/libs/api-gateway-0.0.1-SNAPSHOT.jar
  container_name: api-gateway
  deploy:
    resources:
      limits:
        memory: 512M       # Gateway is lightweight (no JPA, no Kafka consumer)
        cpus: "0.5"
  ports: ["8080:8080"]
  environment:
    SERVER_PORT: "8080"
    SPRING_PROFILES_ACTIVE: dev
    EUREKA_SERVER_URL: http://eureka-server:8761/eureka/
    ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
    REDIS_HOST: redis
    REDIS_PORT: "6379"
    GATEWAY_CORS_ORIGINS: http://localhost:3000
  networks: [insurance-net]
  restart: unless-stopped
  depends_on:
    eureka-server: { condition: service_healthy }
    redis: { condition: service_healthy }
```

### JAR Naming

The API Gateway JAR must match what Gradle builds. Verify the JAR name after build:
```bash
.\gradlew.bat :services:api-gateway:bootJar
ls services/api-gateway/build/libs/
```

It should be `api-gateway-0.0.1-SNAPSHOT.jar`. If not, adjust the `JAR_PATH` in the Docker Compose config.

The Eureka Server does NOT need to be containerized via `Dockerfile.service` — it's infrastructure, like Redis/Zipkin. However, for consistency, we'll add it as a standard Docker Compose service using the official image or the service Dockerfile approach.

Actually, the simplest approach for Eureka: build the JAR and use the same `Dockerfile.service` pattern. Eureka is a Spring Boot app just like any other service. Or use `eclipse-temurin:25-jre-alpine` directly in the compose file (less consistent with other services).

For consistency, use the `Dockerfile.service` approach — same as all other services.

---

## Steps

### Step 1: Add Eureka Server to `docker-compose.yml`

**File:** `infra/docker/docker-compose.yml`

Add the Eureka Server service definition. Place it after the `redis` service (near the other infrastructure services, before the `volumes:` section):

```yaml
  # ============================================================
  # Eureka Service Discovery
  # ============================================================
  eureka-server:
    build:
      context: ../../
      dockerfile: infra/docker/Dockerfile.service
      args:
        JAR_PATH: services/eureka-server/build/libs/eureka-server-0.0.1-SNAPSHOT.jar
    container_name: eureka-server
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: "0.5"
    ports:
      - "8761:8761"
    environment:
      SERVER_PORT: "8761"
      SPRING_PROFILES_ACTIVE: dev
      ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
    networks:
      - insurance-net
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8761/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
```

Key decisions:
- Uses `Dockerfile.service` — same pattern as all other services
- JAR_PATH: `services/eureka-server/build/libs/eureka-server-0.0.1-SNAPSHOT.jar`
- Port `8761:8761` — standard Eureka port
- Health check via Actuator `/actuator/health` endpoint (requires `spring-boot-starter-actuator` — already in Eureka's build.gradle.kts from Plan 01)
- Memory: 512M (Eureka is lightweight — no DB, no Kafka)

### Step 2: Remove Eureka port from docker-compose.override.yml (if needed)

**File:** `infra/docker/docker-compose.override.yml`

The port mapping `8761:8761` is already in the main `docker-compose.yml` for consistency (like Zipkin on `9411:9411`). No override needed.

### Step 3: Add API Gateway to `docker-compose.services.yml`

**File:** `infra/docker/docker-compose.services.yml`

Add the Gateway service definition. Place it BEFORE the existing services (it's the entry point):

```yaml
  # ============================================================
  # API Gateway — single entry point for all external requests
  # ============================================================
  api-gateway:
    build:
      context: ../../
      dockerfile: infra/docker/Dockerfile.service
      args:
        JAR_PATH: services/api-gateway/build/libs/api-gateway-0.0.1-SNAPSHOT.jar
    container_name: api-gateway
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: "0.5"
    ports: ["8080:8080"]
    environment:
      SERVER_PORT: "8080"
      SPRING_PROFILES_ACTIVE: dev
      EUREKA_SERVER_URL: http://eureka-server:8761/eureka/
      ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
      REDIS_HOST: redis
      REDIS_PORT: "6379"
      GATEWAY_CORS_ORIGINS: http://localhost:3000
    networks: [insurance-net]
    restart: unless-stopped
    depends_on:
      eureka-server: { condition: service_healthy }
      redis: { condition: service_healthy }
```

Important: `eureka-server` is defined in `docker-compose.yml` (the infrastructure file), not in `docker-compose.services.yml`. The `depends_on` can reference services across compose files when they share the same network (`insurance-net`) and are launched together.

### Step 4: Add Eureka config to all existing services in `docker-compose.services.yml`

For each of the 6 existing services in `docker-compose.services.yml`, add two things:

**4a. Add `EUREKA_SERVER_URL` environment variable:**

Add this line to each service's `environment:` section:
```yaml
      EUREKA_SERVER_URL: http://eureka-server:8761/eureka/
```

**4b. Add `eureka-server` to `depends_on`:**

For each service, add the health condition dependency:
```yaml
    depends_on:
      kafka: { condition: service_healthy }
      <service>-db: { condition: service_healthy }
      eureka-server: { condition: service_healthy }       # <-- ADD THIS
```

**Services to update:**
1. customer-service
2. vehicle-service
3. realestate-service
4. insurance-service
5. estimation-service
6. reference-data-service

### Step 5: Verify Eureka Server application.yml for Docker

Re-read `services/eureka-server/src/main/resources/application.yml` from Plan 01. Ensure:
- `server.port` is configurable via environment variable: `server.port: ${SERVER_PORT:8761}`
- Zipkin endpoint is configurable: `management.tracing.export.zipkin.endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}`

If it's NOT configurable (hardcoded), update it. The Docker environment passes these via env vars.

Actually, Spring Boot automatically maps environment variables to properties. `SERVER_PORT` → `server.port`, `ZIPKIN_ENDPOINT` → `management.tracing.export.zipkin.endpoint`. But the `application-common.yml` already has:
```yaml
management:
  tracing:
    export:
      zipkin:
        endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

So Zipkin is covered. For `SERVER_PORT`, the Eureka `application.yml` should use:
```yaml
server:
  port: ${SERVER_PORT:8761}
```

Re-read the file and update if needed. Same check for Gateway's `application.yml`.

### Step 6: Update `start-all.cmd`

**File:** `start-all.cmd` (repo root)

First, read the file to understand current structure.

Then add Eureka Server and API Gateway to the build and startup sequence:

**Build section — add:**
```batch
call .\gradlew.bat :services:eureka-server:bootJar -x test
call .\gradlew.bat :services:api-gateway:bootJar -x test
```

**Docker startup — the command already uses docker-compose files that include `docker-compose.services.yml`, so the API Gateway will start automatically once added there.**

The Eureka server is part of `docker-compose.yml` (infrastructure), so it starts with the infra.

The existing `start-all.cmd` likely runs:
```
.\gradlew.bat bootJar -x test && docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml -f infra/docker/docker-compose.services.yml up -d --build
```

Or builds each service separately. The `bootJar -x test` approach for all services is correct.

### Step 7: Update `stop-all.cmd`

**File:** `stop-all.cmd` (repo root)

Likely just runs:
```batch
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml -f infra/docker/docker-compose.services.yml down
```

This will automatically stop the Gateway and Eureka too (since they're in the compose files). No changes needed unless the script lists services explicitly.

Read the file to verify and update if needed.

### Step 8: Update `.env.template`

**File:** `.env.template` (repo root)

Add/verify these entries exist:

```properties
# Eureka Service Discovery
EUREKA_SERVER_URL=http://localhost:8761/eureka/

# Redis (API Gateway rate limiting)
REDIS_HOST=localhost
REDIS_PORT=6379

# API Gateway
GATEWAY_PORT=8080

# Gateway CORS (comma-separated origins)
GATEWAY_CORS_ORIGINS=http://localhost:3000
```

If any already exist (like `REDIS_HOST`, `REDIS_PORT`, `GATEWAY_PORT`), verify the values are correct. Do NOT duplicate.

### Step 9: Update `infra/docker/.env`

**File:** `infra/docker/.env`

This file is used by Docker Compose for variable substitution. Verify it has:
```
POSTGRES_USER=ims_user
POSTGRES_PASSWORD=ims_password
```

These are the only vars referenced in `docker-compose.yml` (`${POSTGRES_USER}`, `${POSTGRES_PASSWORD}`). The service-specific vars are passed via `environment:` blocks, not `.env`. No changes needed unless `.env` is missing values.

### Step 10: Build and test with Docker Compose

```bash
# Rebuild all JARs
.\gradlew.bat :services:eureka-server:bootJar -x test
.\gradlew.bat :services:api-gateway:bootJar -x test
.\gradlew.bat :services:customer-service:bootJar -x test

# Start all infra + gateway + services
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml -f infra/docker/docker-compose.services.yml up -d --build

# Check status
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.services.yml ps

# Verify Eureka dashboard
# Browser: http://localhost:8761

# Verify Gateway routing
curl http://localhost:8080/api/reference-data/cities

# Check Gateway logs
docker logs api-gateway
```

### Step 11: Verify service registration in Eureka

After all services are up, check `http://localhost:8761` — all registered services should appear:
- `CUSTOMER-SERVICE`
- `VEHICLE-SERVICE`
- `REALESTATE-SERVICE`
- `INSURANCE-SERVICE`
- `ESTIMATION-SERVICE`
- `REFERENCE-DATA-SERVICE`
- `API-GATEWAY`

### Step 12: Test end-to-end Gateway routing

```bash
# Public route (no auth)
curl -v http://localhost:8080/api/reference-data/cities

# Auth route (expect JWT error since Auth Service is stub)
curl -v http://localhost:8080/api/customers
# Should return 401 with standardized error

# Gateway health
curl http://localhost:8080/actuator/health
```

---

## Acceptance Criteria

- [x] Eureka Server added to `docker-compose.yml` with health check
- [x] API Gateway added to `docker-compose.services.yml`
- [x] All 6 existing services have `EUREKA_SERVER_URL` env var in Docker Compose
- [x] All 6 existing services have `eureka-server` in `depends_on`
- [x] Eureka Server `application.yml` uses `${SERVER_PORT:8761}` for port
- [x] `start-all.cmd` updates (port listing, service count)
- [x] `stop-all.cmd` stops all services correctly (no changes needed — Docker Compose handles it)
- [x] `.env.template` has all new environment variables (already present)
- [ ] `docker compose up` starts Eureka and Gateway without errors
- [ ] Eureka dashboard accessible at `http://localhost:8761`
- [ ] All services register with Eureka (visible in dashboard)
- [ ] Gateway accessible at `http://localhost:8080`
- [ ] Gateway forwards requests to downstream services (test with reference-data)
- [ ] Gateway returns 401 for authenticated routes without token
