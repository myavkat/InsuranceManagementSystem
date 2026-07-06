# Environment Quirks Outline

Non-obvious environment-specific details that affect development and troubleshooting.

---

## Git Ignore Coverage

- **Root `.gitignore`** — covers IDE/OS artifacts, `node_modules/`, `build/`, `.env` files, logs, and test output.
- **Per-module `.gitignore`** files also present in:
  - `backend/`
  - `frontend/`
  - Each `services/*/`

---

## Database Defaults

### Legacy Backend (Monolith)

See [`08_LEGACY_BACKEND.md`](./08_LEGACY_BACKEND.md) for legacy connection details.

### Target Services

- Connection pattern: `jdbc:postgresql://localhost:5432/<service-db>`
- One database per service (see [`02_MICROSERVICES_SPECIFICATIONS.md`](./02_MICROSERVICES_SPECIFICATIONS.md))
- Configured in: each service's `application.yml`

---

## Message Broker Defaults

Kafka runs via Docker Compose. Default connection string:

- **Kafka:** `localhost:9092`

Topics are pre-provisioned by the `kafka-init` container on first startup. See `infra/kafka/create-topics.sh` for the full topic configuration.

If a service fails to start with connection errors, verify Docker Compose is running:
```bash
docker compose -f infra/docker/docker-compose.yml ps
```

---

## Testcontainers on Windows

Integration tests use Testcontainers (`@Testcontainers`, `PostgreSQLContainer`, `ConfluentKafkaContainer`) which require Docker. On Windows with Docker Desktop in WSL2 mode, the default named pipe auto-detection may fail with:

```
Could not find a valid Docker environment.
```

**Fix:** Add the WSL2 pipe to `~/.testcontainers.properties`:

```properties
docker.host=npipe:////./pipe/dockerDesktopLinuxEngine
```

Note: the standard pipe is `docker_engine`; Docker Desktop in WSL2 mode uses `dockerDesktopLinuxEngine`. If a `docker.client.strategy` line exists from an older Testcontainers 1.x setup, remove it — Testcontainers 2.x resolves the strategy automatically from `docker.host`.

---

## IDE Quirks

### IntelliJ IDEA

- `out/` directory at repo root — IntelliJ output directory (from `.idea` config).
- This directory is gitignored.

### Gradle on Windows

- Always use `.\gradlew.bat` (PowerShell/CMD) or `./gradlew` (Git Bash/WSL).
- The root `settings.gradle.kts` includes all subprojects — IntelliJ should auto-detect modules.

---

## Legacy Stack Coexistence

During incremental migration (see [`01_SYSTEM_ARCHITECTURE.md`](./01_SYSTEM_ARCHITECTURE.md) rule #6):

- **Legacy Vue app** → served from `app.legacy.example.com`
- **New Next.js app** → served from `app.example.com`
- **API Gateway** routes traffic by `Host` header or migration cookie
- **Legacy monolith `backend/`** → remains on its own subdomain/port

---

## Port Allocation (Target Services)

| Service | Default Port |
|---------|-------------|
| `customer-service` | `8081` |
| `vehicle-service` | `8082` |
| `realestate-service` | `8083` |
| `insurance-service` | `8084` |
| `estimation-service` | `8085` |
| `reference-data-service` | `8086` |
| `auth-service` | `8087` |
| `api-gateway` | `8080` |
| Legacy backend | (existing port, unchanged) |
| Next.js frontend | `3000` |
| Zipkin | `9411` |
