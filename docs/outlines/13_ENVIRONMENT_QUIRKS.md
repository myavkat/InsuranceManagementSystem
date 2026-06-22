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

- Connection: `r2dbc:pool:mssql://localhost/InsuranceDB`
- Credentials: `sa` / `123456`
- Configured in: `backend/src/main/resources/application.properties`

### Target Services

- Connection pattern: `jdbc:postgresql://localhost:5432/<service-db>`
- One database per service (see [`02_MICROSERVICES_SPECIFICATIONS.md`](./02_MICROSERVICES_SPECIFICATIONS.md))
- Configured in: each service's `application.yml`

---

## Message Broker Defaults

Both run via Docker Compose. Default connection strings:

- **Kafka:** `localhost:9092`
- **RabbitMQ:** `localhost:5672`
- **RabbitMQ Management UI:** `http://localhost:15672` (guest/guest)

If a service fails to start with connection errors, verify Docker Compose is running:
```bash
docker compose -f infra/docker/docker-compose.yml ps
```

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

During incremental migration:

- **Legacy Vue app** → served from `app.legacy.example.com`
- **New Next.js app** → served from `app.example.com`
- **API Gateway** routes traffic by `Host` header or migration cookie
- **Legacy monolith `backend/`** → remains on its own subdomain/port
- **Do not modify the legacy stack** unless explicitly directed for maintenance

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
