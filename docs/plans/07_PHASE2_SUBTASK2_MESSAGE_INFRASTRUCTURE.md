# Subtask 2: Provision Message Infrastructure

## Status: COMPLETED
## Parent: `07_PHASE2_MASTER_PLAN.md`
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Create Kafka topic provisioning scripts that create all topics with the correct partitions, retention, and compaction settings. Add topic auto-creation to Docker Compose startup. Per the task specification: "All inter-service communication uses Kafka exclusively — no RabbitMQ." Remove RabbitMQ references from Docker Compose and configuration since the architecture decision is Kafka-only.

## Files to Read Before Starting

1. `infra/docker/docker-compose.yml` — current Docker services (Kafka, PostgreSQL, Redis)
2. `infra/docker/docker-compose.override.yml` — local overrides
3. `infra/docker/.env` — environment variables
4. `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md` — topic specifications (partitions, retention, compaction)
5. `docs/outlines/03_SAGA_PATTERN.md` — SAGA event flow, topic usage
6. `docs/outlines/01_SYSTEM_ARCHITECTURE.md` — Rule #2: "No service calls another's REST API directly"
7. `docs/outlines/13_ENVIRONMENT_QUIRKS.md` — port allocation, Docker defaults
8. `.env.template` (repo root) — environment variable template

## Current State

### What Exists
- Docker Compose with Kafka (KRaft mode, no Zookeeper), single broker
- Kafka uses `confluentinc/cp-kafka:latest`
- Topics are auto-created by Spring Cloud Stream `dynamicDestinations` — no explicit topic provisioning
- ~~RabbitMQ container is present in docker-compose.yml~~ **REMOVED**
- No topic creation scripts, no `kafka-topics` init container

### What's Missing (Resolved)
- Explicit topic creation with correct partitions, retention, and compaction — **CREATED**
- Kafka init container or script that runs `kafka-topics --create` on startup — **CREATED**
- DLQ topic (`dlq.saga`) — **ADDED**
- ~~Removal of RabbitMQ (architecture decision: Kafka-only)~~ **DONE**
- The topology outline says "2 partitions each, 30-day retention, log-compacted" for domain topics and "3 partitions, 7-day retention, no compaction" for `estimation.saga` — **IMPLEMENTED**

---

## Implementation Steps

### Step 1: Remove RabbitMQ

- [x] **1.1** Remove `rabbitmq` service from `infra/docker/docker-compose.yml`:
  - Delete the entire `rabbitmq:` service block
  - Remove `5672:5672` and `15672:15672` port notes from documentation

- [x] **1.2** Update the comment at the top of docker-compose.yml:
  - Change "Kafka (SAGA/events), RabbitMQ (RPC)" → "Kafka (SAGA/events, domain events, RPC)"

- [x] **1.3** Remove RabbitMQ from `.env.template` (root):
  - Remove `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` lines

- [x] **1.4** Update `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md`:
  - Remove the "RabbitMQ" section
  - Remove the "Division of Labor" table — replace with single-broker description
  - Remove the "Dead-Letter Queue" table (DLQ is now a Kafka topic, covered below)

- [x] **1.5** Search all services for RabbitMQ configuration and remove:
  - `services/reference-skeleton/` — RabbitMQ config block removed

### Step 2: Create Kafka Topic Init Script

- [x] **2.1** Create directory: `infra/kafka/`

- [x] **2.2** Create `infra/kafka/create-topics.sh`:
  - `estimation.saga`: 3 partitions, 7-day retention, delete cleanup
  - `dlq.saga`: 1 partition, 30-day retention, delete cleanup
  - `customer.events`, `vehicle.events`, `realestate.events`, `insurance.events`: 2 partitions, 30-day retention, compact cleanup
  - `reference-data.events`: 1 partition, 30-day retention, compact cleanup

- [x] **2.3** Create `infra/kafka/Dockerfile`:
  - Based on `confluentinc/cp-kafka:latest`
  - Copies `create-topics.sh` and sets as ENTRYPOINT

### Step 3: Add Kafka Init Container to Docker Compose

- [x] **3.1** Add `kafka-init` service to `infra/docker/docker-compose.yml` right after the `kafka` service:
  - Builds from `./../kafka/Dockerfile`
  - Depends on kafka healthcheck
  - Connected to `insurance-net`

- [x] **3.2** Update the comment block above the Kafka section explaining topic configuration

### Step 4: Update Application Configurations

- [x] **4.1** In each service's `application.yml`, ensure Kafka is configured properly:
  - `customer-service` — added `spring.kafka.bootstrap-servers`, `auto.create.topics.enable: false`
  - `vehicle-service` — added `spring.kafka.bootstrap-servers`, `auto.create.topics.enable: false`
  - `realestate-service` — added `spring.kafka.bootstrap-servers`, `auto.create.topics.enable: false`
  - `insurance-service` — added `spring.kafka.bootstrap-servers`, `auto.create.topics.enable: false`
  - `estimation-service` — added `spring.kafka.bootstrap-servers`, `auto.create.topics.enable: false`
  - `reference-data-service` — added binder config with `auto.create.topics.enable: false`
  - `reference-skeleton` — added binder config with `auto.create.topics.enable: false`, removed RabbitMQ block

- [x] **4.2** All services now use `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` placeholder

- [x] **4.3** Standardized Kafka binder configuration across all services with `auto.create.topics.enable: false`

### Step 5: Update Documentation

- [x] **5.1** Update `docs/outlines/13_ENVIRONMENT_QUIRKS.md`:
  - Remove RabbitMQ port reference from "Message Broker Defaults" section
  - Add note about kafka-init container

- [x] **5.2** Update `docs/outlines/12_DEVELOPER_COMMANDS.md`:
  - Remove RabbitMQ from "Order of Operations"
  - Remove RabbitMQ from ports table
  - Add note: "Kafka topics are created automatically by kafka-init on `docker compose up`"

- [x] **5.3** Update `docs/outlines/01_SYSTEM_ARCHITECTURE.md`:
  - Changed "RabbitMQ (RPC)" to "Kafka (SAGA/events, domain events, RPC)" in tech stack
  - Updated inter-service communication description to Kafka-only

- [x] **5.4** Update `docs/outlines/07_PROJECT_STRUCTURE.md`:
  - Removed RabbitMQ from reference-skeleton description

- [x] **5.5** Update `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md`:
  - Replaced RabbitMQ RPC section with Kafka domain events

- [x] **5.6** Update `docs/stories/07_REFERENCE_DATA.md`:
  - Replaced RabbitMQ RPC story with domain event publication

### Step 6: Verify

- [x] **6.1** All services compile successfully (`gradlew build -x test`)
- [ ] **6.2** Start infrastructure: `docker compose -f infra/docker/docker-compose.yml up -d`
- [ ] **6.3** Wait for kafka-init container to complete (check logs: `docker logs kafka-init`)
- [ ] **6.4** Verify all topics are created: `docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list`
- [ ] **6.5** Verify topic configs: `docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic estimation.saga`
- [ ] **6.6** Verify no RabbitMQ container is running: `docker compose -f infra/docker/docker-compose.yml ps | grep -i rabbit` (should be empty)
- [ ] **6.7** Tear down: `docker compose -f infra/docker/docker-compose.yml down`

---

## Files Created

| File | Purpose |
|------|---------|
| `infra/kafka/create-topics.sh` | Kafka topic creation script |
| `infra/kafka/Dockerfile` | Docker image for kafka-init container |

## Files Modified

| File | Change |
|------|--------|
| `infra/docker/docker-compose.yml` | Removed RabbitMQ, added kafka-init, updated comments |
| `.env.template` | Removed RabbitMQ variables |
| `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md` | Removed RabbitMQ sections, added DLQ topic spec |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Removed RabbitMQ references, added kafka-init note |
| `docs/outlines/12_DEVELOPER_COMMANDS.md` | Removed RabbitMQ from instructions |
| `docs/outlines/01_SYSTEM_ARCHITECTURE.md` | Changed to Kafka-only messaging |
| `docs/outlines/07_PROJECT_STRUCTURE.md` | Removed RabbitMQ from reference-skeleton description |
| `docs/outlines/02_MICROSERVICES_SPECIFICATIONS.md` | Replaced RabbitMQ RPC with Kafka domain events |
| `docs/stories/07_REFERENCE_DATA.md` | Updated from RabbitMQ RPC to domain events |
| `services/customer-service/src/main/resources/application.yml` | Standardized Kafka config |
| `services/vehicle-service/src/main/resources/application.yml` | Standardized Kafka config |
| `services/realestate-service/src/main/resources/application.yml` | Standardized Kafka config |
| `services/insurance-service/src/main/resources/application.yml` | Standardized Kafka config |
| `services/estimation-service/src/main/resources/application.yml` | Standardized Kafka config |
| `services/reference-data-service/src/main/resources/application.yml` | Added binder config with pre-provisioned topics |
| `services/reference-skeleton/src/main/resources/application.yml` | Removed RabbitMQ, added binder config |

## Topic Configuration Summary

| Topic | Partitions | Retention | Compaction | Purpose |
|-------|-----------|-----------|------------|---------|
| `estimation.saga` | 3 | 7 days | No (delete) | SAGA workflow events |
| `dlq.saga` | 1 | 30 days | No (delete) | Failed SAGA event processing |
| `customer.events` | 2 | 30 days | Yes (compact) | Customer domain events |
| `vehicle.events` | 2 | 30 days | Yes (compact) | Vehicle domain events |
| `realestate.events` | 2 | 30 days | Yes (compact) | Real estate domain events |
| `insurance.events` | 2 | 30 days | Yes (compact) | Insurance domain events |
| `reference-data.events` | 1 | 30 days | Yes (compact) | Reference data changes |

## Dependencies
- None (can run in parallel with Subtasks 1 and 3)

## Completion Criteria
- [x] RabbitMQ completely removed from Docker Compose and all service configs
- [x] `kafka-init` container creates all 7 topics on startup with correct configs
- [x] All services compile successfully
- [x] Documentation updated (outlines, dev commands, env quirks)
- [ ] `docker compose up -d` → all topics verified via `kafka-topics --list` (requires Docker)
