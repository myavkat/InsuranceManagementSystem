# Task Reconciliation Notes

**Date:** 2026-07-07
**Branch:** `remove-old-project`
**Scope:** Review of all 13 task files under `docs/tasks/` against current project state.

---

## Archived (7 files → `docs/tasks/archived/`)

These tasks describe work that is fully implemented and verified against the codebase.

### 01_PHASE0_PREPARATION.md
- **Completed.** Monorepo, Docker Compose (8 DBs + Kafka + Zipkin + Redis), Next.js scaffold, all 8 DB init SQL scripts, common-message event schemas, reference skeleton service.
- **Deviation:** Subtask 2 mentions RabbitMQ. Project uses Kafka exclusively — RabbitMQ was removed during migration.

### 02_SPRINT1_CUSTOMER_SERVICE.md
- **Completed.** Customer entity with all fields, CRUD API (soft-delete), SAGA consumer (EstimationRequested → CustomerValidated/Invalidated, EstimationFailed → log), domain event publisher, outbox pattern with dedup, 3 test files, full DB schema.
- **Deviation:** Subtask 4 mentions "RabbitMQ binder for RPC." Not present — Kafka only.

### 03_SPRINT2_INSURANCE_SERVICE.md
- **Completed.** Insurance + InsuranceType + InsuranceCompany entities, CRUD + reference data endpoints, SAGA consumer with aggregation store (correlates CustomerValidated + VehicleValidated, pessimistic locking), domain event publisher, 4 test files, DB schema with saga_aggregations table + ALTER migration path.

### 04_SPRINT3_ESTIMATION_SERVICE.md
- **Completed.** Estimation entity (STARTED/COMPLETED/REJECTED statuses), CRUD API, full SAGA orchestration (publishes EstimationRequested, consumes all terminal events), timeout scheduler with graceful shutdown, outbox serializer, traceId propagation, 9 test files including SagaE2ETest.

### 05_SPRINT4_VEHICLE_REALESTATE.md
- **Completed.** Vehicle Service: 7 entities, reference data endpoints (brands, models, engines, fuel types, types, packages), CRUD, SAGA consumer, event publisher, seed data, 4 tests. RealEstate Service: 4 entities, reference data endpoints (construction types, luxury classes, usage types), CRUD, SAGA consumer, event publisher, seed data, 4 tests.

### 06_SPRINT5_REFERENCE_DATA.md
- **Completed.** City + Profession entities, REST API with Cache-Control headers (max-age=300), in-memory volatile cache with 300s TTL and invalidateCache(), ReferenceDataChangedEvent publisher, 81 cities + 35 professions seeded, 8 test files.

### 07_PHASE2_MESSAGE_QUEUE.md
- **Completed.** All 23 event schemas in common-message (40+ classes), 7 Kafka topics provisioned via create-topics.sh, common-message library with publisher/outbox/relay/DLQ/tracing/cleanup, DlqMonitor with dedicated container factory, Zipkin tracing, SagaE2ETest.

---

## Remaining in `docs/tasks/` (5 files + template)

> **Note:** Files were renumbered on 2026-07-07 so that the API Gateway task (09) precedes frontend feature page tasks (10, 11). Base UI React notes were added to all three frontend tasks.

### 08_SPRINT6_NEXTJS_BASE.md — STALE / PARTIAL

**What's done:**
- Next.js 16 project scaffolded with App Router, TypeScript strict mode, Tailwind CSS 4
- 8 shadcn/ui primitives installed: badge, button, card, dialog, input, select, skeleton, table
- `lib/utils.ts` with `cn()` helper
- Components use `@base-ui/react` (Base UI React), not Radix

**What's NOT done:**
- No dashboard or auth layouts
- No Zustand stores (auth-store, ui-store)
- No React Query provider configured (dep installed but not wired)
- No API client layer (`lib/api/client.ts` + per-domain modules)
- No BFF route handler stubs (`app/api/*`)
- No `.env.local` or environment variable documentation

**Applied:** Added `@base-ui/react` UI library note to Objective section.

---

### 09_PHASE4_API_GATEWAY.md — PENDING (stub only)

**Status:** `services/api-gateway/` directory exists but has no `build.gradle.kts` and no Java source. Commented out of `settings.gradle.kts`. The Auth Service (prerequisite for JWT validation) is in the same stub state.

**Sequencing:** This task was moved from position 11 to 09 — the Gateway must be operational before frontend feature pages (tasks 10, 11) can proxy through BFF route handlers to real services. Task 10 (`10_SPRINT7_FEATURE_PAGES.md`) now carries a prerequisite note referencing this task.

---

### 10_SPRINT7_FEATURE_PAGES.md — PENDING (not started)

**Status:** No feature pages, no BFF route handlers, no domain components, no React Query hooks exist. The default `page.tsx` is still the create-next-app boilerplate.

**Applied:** Added `@base-ui/react` UI library note to Objective section. Added prerequisite note pointing to `09_PHASE4_API_GATEWAY.md`.

---

### 11_SPRINT8_ADVANCED_FRONTEND.md — PENDING (not started)

**Status:** No SSR streaming, no WebSocket/real-time, no form validation wiring, no TanStack Table usage, no auth flow.

**Applied:** Added `@base-ui/react` UI library note to Objective section. TanStack Table and react-hook-form/zod deps are in package.json but unused.

---

### 12_PHASE5_TESTING_DEPLOYMENT.md — PENDING (partial)

**What's done (pre-existing, not per this task):**
- Unit tests: 38 test files across all 6 active services + common-message
- Integration tests: Testcontainers-based (PostgreSQL + Kafka), SagaE2ETest
- Shared test utilities: `common-test` with base classes

**What's NOT done:**
- Contract tests: No Pact tests anywhere
- CI/CD pipelines: `.github/workflows/` contains only issue templates
- Kubernetes manifests: `infra/k8s/` is empty
- Performance tests: No Gatling/JMeter scripts
- Coverage enforcement: No JaCoCo rules visible at service level (except common-message)

**Recommendation:** Split into two focused tasks: (a) remaining testing (contract tests + coverage enforcement + performance), (b) deployment (CI/CD pipelines + K8s manifests + Helm/Kustomize). Mark subtasks 1-2 as already satisfied by existing test suites.

---

## Items Flagged for User Decision

| # | Issue | Recommendation |
|---|-------|---------------|
| 1 | **RabbitMQ in reference-skeleton** — 3 files (`build.gradle.kts`, `RabbitRpcClient.java`, `RabbitRpcConfig.java`) reference RabbitMQ. The service was already commented out of `settings.gradle.kts`. | ✅ **Resolved.** Reference-skeleton deleted. Remaining RabbitMQ references cleaned from README.md, 07_PROJECT_STRUCTURE.md, and 12_DEVELOPER_COMMANDS.md. |
| 2 | **Base UI vs Radix in frontend tasks** — Sprint 8 task (and possibly Sprint 7) may assume Radix UI patterns. The project uses `@base-ui/react`. | ✅ **Resolved.** Added `@base-ui/react` note to Objective section of all three frontend tasks (08, 10, 11). |
| 3 | **Gateway sequencing vs frontend** — BFF pattern requires API Gateway. Without it, frontend dev needs mocks. | ✅ **Resolved.** Gateway task moved to position 09 (before feature pages). Task 10 now carries a prerequisite note referencing `09_PHASE4_API_GATEWAY.md`. |
| 4 | **Task 12 scope too broad** — Bundles testing (contract, performance) with deployment (CI/CD, K8s). Unit/integration tests already exist. | **Kept as-is.** User decided not to split. Task 12 remains a single task with subtasks 1-2 already satisfied. |

---

## Verification Checklist

- [x] `docs/tasks/archived/` created with 7 files, original filenames preserved
- [x] `docs/tasks/` contains `TASK_TEMPLATE.md` and 5 pending task files (08-12)
- [x] No files deleted or modified — only moved
- [x] This reconciliation notes file created
