# Plan 13-08: Auth Service — Docker Compose Integration

**Objective:** Add the auth-service container definition to `docker-compose.services.yml` so it can be deployed alongside the other microservices.

**Depends on:** Plan 13-01 (the service module, build.gradle.kts, and application.yml must exist — the Dockerfile builds the JAR). Does not depend on entities, service, or controller code being complete.

**Estimated files to modify:** 1

---

## Files to Read First

Before writing any code, open these files:

| File | Why |
|------|-----|
| `infra/docker/docker-compose.services.yml` | Understand the exact structure and pattern of existing service container definitions |
| `infra/docker/docker-compose.yml` | Check if auth-db is already defined in the infra compose file |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Port 8087 for auth-service |
| `.env.template` | AUTH_DB_* variables for Docker environment |

---

## Pattern Reference (from existing service definitions)

Each service in `docker-compose.services.yml` follows this pattern:

```yaml
  service-name:
    build:
      context: ../../
      dockerfile: infra/docker/Dockerfile.service
      args:
        JAR_PATH: services/service-name/build/libs/service-name-0.0.1-SNAPSHOT.jar
    container_name: service-name
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: "1.0"
    ports: ["808X:8080"]
    environment:
      SERVER_PORT: "8080"
      SPRING_PROFILES_ACTIVE: dev
      KAFKA_BOOTSTRAP_SERVERS: kafka:9094          # Only if it uses Kafka
      ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
      EUREKA_SERVER_URL: http://eureka-server:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://<db-host>:5432/<db-name>
      OPENAPI_SERVER_URL: http://localhost:808X
    networks: [insurance-net]
    restart: unless-stopped
    depends_on:
      kafka: { condition: service_healthy }         # Only if it uses Kafka
      <db-host>: { condition: service_healthy }
      eureka-server: { condition: service_healthy }
```

---

## Steps

### Step 1: Check if auth-db exists in `docker-compose.yml`

Open `infra/docker/docker-compose.yml` and check if an `auth-db` PostgreSQL container is already defined. 

- If it exists: note its service name and health check configuration. The auth-service will depend on it.
- If it does NOT exist: **do not add it in this plan.** The database container belongs in `docker-compose.yml` (infrastructure), not `docker-compose.services.yml`. Note this as a prerequisite gap and continue with the service definition only.

From the task file, auth_db already exists with its schema at `infra/sql/auth_db/init.sql`, so auth-db should already be in `docker-compose.yml`. Verify this before proceeding.

### Step 2: Add auth-service container to `docker-compose.services.yml`

**File:** `infra/docker/docker-compose.services.yml`

Add the auth-service definition **before the `networks:` block at the end of the file**. Insert it after the `reference-data-service:` block and before the `networks:` line.

The auth service has **no Kafka dependency** — it is a synchronous service. Its `depends_on` section omits Kafka.

```yaml
  auth-service:
    build:
      context: ../../
      dockerfile: infra/docker/Dockerfile.service
      args:
        JAR_PATH: services/auth-service/build/libs/auth-service-0.0.1-SNAPSHOT.jar
    container_name: auth-service
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: "0.5"
    ports: ["8087:8080"]
    environment:
      SERVER_PORT: "8080"
      SPRING_PROFILES_ACTIVE: dev
      ZIPKIN_ENDPOINT: http://zipkin:9411/api/v2/spans
      EUREKA_SERVER_URL: http://eureka-server:8761/eureka/
      SPRING_DATASOURCE_URL: jdbc:postgresql://auth-db:5432/auth_db
      OPENAPI_SERVER_URL: http://localhost:8087
    networks: [insurance-net]
    restart: unless-stopped
    depends_on:
      auth-db: { condition: service_healthy }
      eureka-server: { condition: service_healthy }
```

Key differences from other services:
- **No Kafka** in `depends_on` — auth-service has no SAGA consumers
- **No `KAFKA_BOOTSTRAP_SERVERS`** env var — auth service doesn't connect to Kafka
- **No `OUTBOX_POLL_INTERVAL_MS`** — auth service has no outbox
- **Port mapping:** `8087:8080` — host port 8087 maps to container port 8080
- **Memory:** 512M (lower than the 1G for data-heavy services — auth is lightweight)
- **CPU:** 0.5 (lower than 1.0 for other services)
- `SPRING_DATASOURCE_URL` points to `auth-db:5432/auth_db`

### Step 3: Verify the auth-db container name

Check the actual service name of the auth database in `docker-compose.yml`. The `depends_on` and `SPRING_DATASOURCE_URL` must use the exact container/service name defined there (likely `auth-db`).

If the auth database container uses a different name, update the `depends_on` and `SPRING_DATASOURCE_URL` accordingly.

### Step 4: Validate Docker Compose syntax

Run a syntax check (optional but recommended):
```
docker compose -f infra/docker/docker-compose.services.yml config --quiet
```
Or at minimum, verify the YAML is syntactically valid by inspecting the file.

---

## Acceptance Criteria

- [x] Auth-service container block added to `infra/docker/docker-compose.services.yml` before the `networks:` block
- [x] Container name: `auth-service`
- [x] Port mapping: `8087:8080`
- [x] `depends_on` includes `auth-db` and `eureka-server` (NOT kafka)
- [x] `SPRING_DATASOURCE_URL` points to `jdbc:postgresql://auth-db:5432/auth_db`
- [x] `OPENAPI_SERVER_URL` is `http://localhost:8087`
- [x] Environment includes `SERVER_PORT`, `SPRING_PROFILES_ACTIVE`, `ZIPKIN_ENDPOINT`, `EUREKA_SERVER_URL`
- [x] No Kafka-related environment variables
- [x] Resource limits: 512M memory, 0.5 CPU
- [x] File parses as valid YAML (no syntax errors)
