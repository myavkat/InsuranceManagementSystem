# AGENTS.md - InsuranceManagementSystem

## Global Execution Constraints

### Context Precedence
When working on features, prioritize guidelines in this order:
1. Local Constraints @AGENTS.LOCAL.md - if present in active memory)
2. Project Constraints (This file)
3. Workspace Context & Workflow Handling @docs/AGENTS.md

### Operational Rules
- ALWAYS look into `docs/plans/` for the active feature plan before writing code.
- BEFORE executing any workspace actions, read and adhere to the directory workflow defined in docs/AGENTS.md.
- If a model swap occurred, verify state by cross-referencing code against the active plan file.
- DO NOT invent dependencies or refactor out-of-scope modules.

## Project Structure

- `backend/` — **Legacy** monolith Java Spring Boot WebFlux app (Gradle, Java 25, Spring Boot 4.0.6). Will be replaced incrementally.
- `frontend/` — **Legacy** Vue 3 + Vite + TypeScript + TailwindCSS 4. Will be replaced incrementally.
- `services/` — **Target** microservice modules (under construction):
  - `customer-service/` — Customer CRUD, search
  - `insurance-service/` — Insurance products, types, companies
  - `estimation-service/` — Insurance estimation/quote, SAGA coordination
  - `vehicle-service/` — Vehicle information management
  - `realestate-service/` — Real estate information management
  - `reference-data-service/` — Reference data (cities, professions, lookups)
  - `api-gateway/` — Spring Cloud Gateway, routing, auth, rate limiting
  - `auth-service/` — Authentication & JWT issuance/validation
  - `reference-skeleton/` — Reference/template Spring Boot service (CRUD, Kafka, RabbitMQ)

  All target services: **Spring Boot MVC + Spring Data JPA (Hibernate)** + **PostgreSQL**.
  Each service has its own dedicated database (Database per Service pattern).

- `frontend-next/` — **Target** Next.js SSR (App Router) + Tailwind CSS + shadcn/ui frontend.

- `common/` — Shared libraries:
  - `common-message/` — Event schemas (SAGA + domain events), serialization, constants
  - `common-test/` — Shared test utilities

- `infra/` — Infrastructure artifacts:
  - `infra/docker/` — Docker Compose, `.env`, override configs
  - `infra/sql/` — Database init scripts per service (mounted by Docker Compose)
  - `infra/k8s/` — Kubernetes manifests (future)

- Root `settings.gradle.kts` — Unified Gradle multi-project build (all services + common modules)
- Root `.env.template` — Placeholder template for environment variables

## SAGA Pattern — Choreography

Services collaborate exclusively via events; no central orchestrator.

**Rules:**
- Every saga starts with the Estimation Service generating a unique `sagaId`.
- All subsequent events carry the same `sagaId` for correlation.
- Each service listens for events, performs its local transaction, and publishes the outcome.
- Compensation: if any step fails, `EstimationFailed` triggers rollback in all participating services.
- The Estimation Service implements a timeout — if no terminal event arrives within a configurable window, it publishes `EstimationFailed`.

**SAGA Flow — Create Insurance Estimation:**
1. **Estimation Service** → creates pending `Estimation` (status `STARTED`) → publishes `EstimationRequested` to Kafka `estimation.saga`.
2. **Customer Service** → consumes `EstimationRequested` → validates customer → publishes `CustomerValidated` or `CustomerInvalidated`.
3. **Vehicle Service** → consumes `EstimationRequested` → validates vehicle → publishes `VehicleValidated` or `VehicleInvalidated`.
4. **Insurance Service** → listens for both `CustomerValidated` + `VehicleValidated` (correlated by `sagaId`) → calculates premium → publishes `PremiumCalculated` or `CalculationFailed`.
5. **Estimation Service** → consumes `PremiumCalculated` → updates estimation to `COMPLETED`. If any failure event → estimation set to `REJECTED` and `EstimationFailed` published.
6. **Compensation:** Services listen for `EstimationFailed` and undo reversible changes.

**Cross-cutting:**
- **Idempotency:** Deduplicate events using `sagaId` + event type.
- **Event Persistence:** Kafka stores all events (log-compacted for entity state, standard for saga).
- **Tracing:** Each event carries `traceId` + `sagaId`; services log structured JSON.

## Developer Commands

### Legacy Frontend (Vue 3)
```bash
cd frontend
npm run dev                  # Vite dev server at localhost:5173
npm run build                # npm-run-all2 parallel: type-check + build-only
npm run preview              # Vite preview of production build
npm run type-check           # vue-tsc --build (incremental)
npm run lint                 # oxlint --fix THEN eslint --fix (sequential via run-s)
npm run format               # Prettier (--write src/)
npm run test:unit            # Vitest (jsdom env, excludes e2e/)
npm run test:e2e             # Playwright (npx playwright install first)
```

### Target Frontend (Next.js)
```bash
cd frontend-next
npm run dev                  # Next.js dev server at localhost:3000
npm run build                # Production build (SSR + static generation)
npm run start                # Production server
npm run lint                 # ESLint
npm run test                 # Jest / Vitest
```

### Legacy Backend (Monolith)
```bash
cd backend
./gradlew bootRun            # Dev server (needs MSSQL with InsuranceDB)
./gradlew test               # JUnit 5 + Spring Boot test slice support
./gradlew clean build        # Full build (packages as JAR)
```

### Target Microservice (any service under services/)
```bash
cd services/<service-name>
./gradlew bootRun            # Dev server (needs local PostgreSQL)
./gradlew test               # JUnit 5 + testcontainers
./gradlew clean build        # Full build (packages as JAR)
```

### Infrastructure (Docker Compose)
```bash
# From repo root:
docker compose -f infra/docker/docker-compose.yml up -d
# With local dev overrides:
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml up -d
docker compose -f infra/docker/docker-compose.yml down
```

## Order Matters

- **Legacy code path:** Frontend before commit: `lint -> type-check -> test:unit` must all pass.
- **Target code path:** Changes affecting multiple services must be implemented bottom-up (no circular dependencies). Reference Data → Auth → Customer/Vehicle/RealEstate → Insurance → Estimation → API Gateway. Frontend-next depends only on API Gateway contracts.

## Architecture

### Legacy Backend (Monolith)

- **Hybrid WebFlux + WebMVC** — both starters on classpath. Controllers are `@Controller` (Thymeleaf templates), never `@RestController`.
- **R2DBC with blocking** — repositories return `Mono`/`Flux`, services call `.block(Duration.ofSeconds(...))` (Thymeleaf rendering is synchronous).
- **Result pattern** — services return `Result`/`DataResult<T>` with `success + message` (Turkish toast strings). Subtypes: `SuccessResult`, `ErrorResult`, `SuccessDataResult`, `ErrorDataResult`.
- **Custom converters** — `WebConfig` registers `LocalizedStringToDoubleConverter` / `LocalizedDoubleToStringConverter` for Turkish locale (comma decimal separator). Critical for form submission.
- **Entity mapping** — uses Spring Data R2DBC `@Table` (not JPA `@Entity`).
- **Lombok** — `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor` on all entities/DTOs.
- **Master layout** — all controllers return `"app"` as template name; set `model.addAttribute("controller", ...)` and `model.addAttribute("page", ...)` for Thymeleaf fragment resolution.
- **Service naming** — interfaces in `business/abstracts/`, implementations in `business/concretes/` suffixed `*Manager`.

### Legacy Frontend (Vue 3)

- **All services are mock** — no real API integration. Functions return hardcoded data with `await new Promise(r => setTimeout(r, N))` delays (300-500ms). `authService.ts` accepts any credentials.
- **Auth** — JWT stored in `localStorage` key `jwt_token`. The `useAuth` composable validates on import; `beforeEach` guard redirects unauthenticated users to `/login`.
- **Most views are stubs** — only `LoginView`, `ClientsView`, `AddClientView`, and `ClientDetailView` have real implementations. All others (Dashboard, Policies, Claims, etc.) render a placeholder `<h1>`.
- **Sidebar uses `<a href>`** — navigation causes full page reloads (not `<router-link>`).
- **Style** — `@import 'tailwindcss'` in `style.css`; no custom CSS files. `@/` import alias maps to `src/`.
- **Tests** — one file (`App.spec.ts`) checks for `'You did it!'` text that does not exist; it will fail as-is.
- **Pinia** — only a scaffold `counter` store exists.
- **Vite devtools** — `vite-plugin-vue-devtools` is active in dev.

### Target Microservice Architecture

- **Domain-driven microservices** — each service owns its domain data and logic. No shared database.
- **Spring Boot MVC + JPA** — imperative controllers (`@RestController`), Spring Data JPA repositories, Hibernate ORM.
- **Database per service** — each service has a dedicated PostgreSQL database.
- **Datetime convention** — timestamps (createdAt, updatedAt, deletedAt, etc.) use `java.time.Instant`; date-only fields (birthDate, policyStartDate, etc.) use `java.time.LocalDate`.
- **Inter-service communication** — **no direct REST calls**. All communication via message brokers:
  - **Kafka** — SAGA events, domain events (audit, analytics, eventual consistency). Topics: `estimation.saga`, `*.events`.
  - **RabbitMQ** — Synchronous RPC calls (e.g., fetch reference data), dead-letter handling.
- **API Gateway** — Spring Cloud Gateway routes external requests to the appropriate service, handles authentication validation, rate limiting.
- **Auth Service** — dedicated service for JWT issuance and validation (no more dummy auth).
- **Idempotency** — consumers deduplicate events using `sagaId` + event type.
- **Result pattern** — services use standardized API response envelope.

### Target Frontend (Next.js SSR)

- **App Router (SSR)** — all pages are server components by default, client components where interactivity is needed.
- **BFF layer** — `app/api/*` route handlers call the API Gateway (`GATEWAY_URL` env var).
- **Server Components** — fetch data via BFF on the server; client components use **React Query** against the same BFF routes.
- **Client state** — managed with **Zustand** (replaces Pinia).
- **UI components** — **shadcn/ui** (Radix primitives + Tailwind CSS), replaces Bootstrap.
- **Feature components** — under `components/features/`.
- **Migration** — legacy Vue app remains operational on a separate subdomain during migration. API Gateway routes traffic by `Host` header or cookie.

## Environment Quirks

- Root `.gitignore` exists — covers IDE/OS artifacts, `node_modules/`, `build/`, `.env` files, logs, and test output. Per-module gitignores also present in `backend/`, `frontend/`, and each `services/*/`.
- Legacy backend `application.properties` defaults to `r2dbc:pool:mssql://localhost/InsuranceDB` with `sa`/`123456`.
- Target services use `application.yml` with `spring.datasource.url=jdbc:postgresql://localhost:5432/<service-db>`.
- Kafka expected at `localhost:9092`, RabbitMQ at `localhost:5672` (both via Docker Compose).
- `out/` at repo root — IntelliJ output directory (from `.idea` config).
- Legacy Vue frontend remains at `frontend/`; new Next.js frontend lives at `frontend-next/`.
- The legacy monolith `backend/` and Vue `frontend/` are preserved as-is during incremental migration — never modify the legacy stack unless explicitly directed for maintenance.
