# Task: Phase 5 — Testing & Deployment

## Context Anchors
- Read Blueprint: @docs/outlines/01_SYSTEM_ARCHITECTURE.md
- Read Blueprint: @docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md
- Read Blueprint: @docs/outlines/03_SAGA_PATTERN.md
- Read Blueprint: @docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md
- Read Blueprint: @docs/outlines/05_NEXTJS_FRONTEND.md
- Read Blueprint: @docs/outlines/06_API_GATEWAY_AUTH.md

## Objective
Production-harden the entire system through comprehensive testing (unit, integration, contract, performance) and set up CI/CD pipelines and Kubernetes deployment manifests.

### Subtasks

1. **Write Service Unit Tests**
   - JUnit 5 + Mockito for all microservices.
   - Test service layer business logic in isolation.
   - Test REST controllers with `@WebMvcTest`.
   - Test JPA repositories with `@DataJpaTest` + testcontainers.
   - Target: ≥80% line coverage per service.

2. **Write Integration Tests**
   - Testcontainers-based integration tests per service:
     - PostgreSQL container for database tests.
     - Kafka container for event publishing/consuming tests.
     - RabbitMQ container for RPC tests.
   - Test full request → response cycle through controller → service → repository.
   - Test event-driven flows: publish → consume → verify side effects.

3. **Write Contract Tests**
   - Pact contract tests between services:
     - Gateway ↔ Auth Service (validation contract).
     - Insurance Service ↔ Customer/Vehicle (event schema contracts).
   - Publish contracts to Pact Broker.
   - CI pipeline verifies contracts on both provider and consumer sides.

4. **Setup CI/CD Pipelines**
   - GitHub Actions workflow for each service:
     - `on: push` to feature branches: build + unit tests.
     - `on: pull_request` to `develop`: build + unit + integration + contract tests.
     - `on: push` to `main`: build → test → Docker image build → push to registry.
   - Monorepo-aware: only build services that changed (paths filter).
   - Frontend pipeline: lint → type-check → build → test.
   - Quality gates: test coverage ≥80%, no critical vulnerabilities, Pact contracts verified.

5. **Create Kubernetes Manifests**
   - Per-service manifests:
     - `Deployment.yaml`: resource limits, health checks, rolling update strategy.
     - `Service.yaml`: ClusterIP service.
     - `ConfigMap.yaml`: externalized configuration (DB URLs, broker addresses).
     - `Secret.yaml`: DB passwords, JWT private keys, API keys.
   - Infrastructure manifests: PostgreSQL StatefulSets, Kafka StatefulSet, RabbitMQ StatefulSet, Redis Deployment.
   - Ingress controller for Gateway with TLS termination.
   - Helm chart for umbrella deployment (or Kustomize overlays for dev/staging/prod).

6. **Execute Performance Tests**
   - Gatling or JMeter test suite executed through the API Gateway.
   - Scenarios: create estimation (SAGA), list customers, search vehicles.
   - Ramp-up: 10 → 100 → 500 concurrent users.
   - Success criteria: p95 latency < 500ms, p99 < 1s, error rate < 1%.
   - Test and tune rate limiting thresholds.
   - Report: throughput, latency percentiles, resource utilization, error breakdown.

### Deliverables
- Unit test suite for all services (≥80% coverage)
- Integration test suite with Testcontainers (PostgreSQL + Kafka + RabbitMQ)
- Pact contract tests verified between dependent services
- GitHub Actions CI/CD pipelines for all services and frontend
- Kubernetes manifests for all services and infrastructure
- Helm chart or Kustomize overlays for multi-environment deployment
- Performance test report with tuning recommendations
- Production-ready deployment runbook
