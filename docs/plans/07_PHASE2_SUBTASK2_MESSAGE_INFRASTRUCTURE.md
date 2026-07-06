# Subtask 2: Provision Message Infrastructure

## Status: NOT STARTED
## Parent: `07_PHASE2_MASTER_PLAN.md`
## Branch: `phase2-message-queue-event-driven-integration`

---

## Objective

Create Kafka topic provisioning scripts that create all topics with the correct partitions, retention, and compaction settings. Add topic auto-creation to Docker Compose startup. Per the task specification: "All inter-service communication uses Kafka exclusively — no RabbitMQ." Remove RabbitMQ references from Docker Compose and configuration since the architecture decision is Kafka-only.

## Files to Read Before Starting

1. `infra/docker/docker-compose.yml` — current Docker services (Kafka, RabbitMQ, PostgreSQL, Redis)
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
- RabbitMQ container is present in docker-compose.yml
- No topic creation scripts, no `kafka-topics` init container

### What's Missing
- Explicit topic creation with correct partitions, retention, and compaction
- Kafka init container or script that runs `kafka-topics --create` on startup
- DLQ topic (`dlq.saga`)
- Removal of RabbitMQ (architecture decision: Kafka-only)
- The topology outline says "2 partitions each, 30-day retention, log-compacted" for domain topics and "3 partitions, 7-day retention, no compaction" for `estimation.saga`

---

## Implementation Steps

### Step 1: Remove RabbitMQ

- [ ] **1.1** Remove `rabbitmq` service from `infra/docker/docker-compose.yml`:
  - Delete the entire `rabbitmq:` service block
  - Remove `5672:5672` and `15672:15672` port notes from documentation

- [ ] **1.2** Update the comment at the top of docker-compose.yml:
  - Change "Kafka (SAGA/events), RabbitMQ (RPC)" → "Kafka (SAGA/events, domain events, RPC)"

- [ ] **1.3** Remove RabbitMQ from `.env.template` (root):
  - Remove `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` lines

- [ ] **1.4** Update `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md`:
  - Remove the "RabbitMQ" section
  - Remove the "Division of Labor" table — replace with single-broker description
  - Remove the "Dead-Letter Queue" table (DLQ will be a Kafka topic, covered in Subtask 5)

- [ ] **1.5** Search all services for RabbitMQ configuration and remove:
  - `grep -r "rabbitmq" services/` — remove any `application.yml` blocks
  - `grep -r "rabbitmq" common/` — remove any references
  - The `reference-skeleton` service has explicit RabbitMQ config — remove it

### Step 2: Create Kafka Topic Init Script

- [ ] **2.1** Create directory: `infra/kafka/`

- [ ] **2.2** Create `infra/kafka/create-topics.sh`:
  ```bash
  #!/bin/bash
  # Creates all Kafka topics with correct partitions, retention, and compaction.
  # Called by the kafka-init container on first startup.

  BOOTSTRAP_SERVER=${1:-kafka:9092}
  RETENTION_MS_7D=604800000
  RETENTION_MS_30D=2592000000

  echo "Waiting for Kafka to be ready..."
  while ! kafka-broker-api-versions --bootstrap-server $BOOTSTRAP_SERVER > /dev/null 2>&1; do
    sleep 2
  done
  echo "Kafka is ready. Creating topics..."

  # SAGA topic — 3 partitions, 7-day retention, no compaction
  kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
    --create --if-not-exists \
    --topic estimation.saga \
    --partitions 3 \
    --replication-factor 1 \
    --config retention.ms=$RETENTION_MS_7D \
    --config cleanup.policy=delete

  # DLQ topic — 1 partition, 30-day retention
  kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
    --create --if-not-exists \
    --topic dlq.saga \
    --partitions 1 \
    --replication-factor 1 \
    --config retention.ms=$RETENTION_MS_30D \
    --config cleanup.policy=delete

  # Domain event topics — 2 partitions, 30-day retention, log-compacted
  for TOPIC in customer.events vehicle.events realestate.events insurance.events; do
    kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
      --create --if-not-exists \
      --topic $TOPIC \
      --partitions 2 \
      --replication-factor 1 \
      --config retention.ms=$RETENTION_MS_30D \
      --config cleanup.policy=compact
  done

  # reference-data.events — 1 partition, 30-day retention, log-compacted
  kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
    --create --if-not-exists \
    --topic reference-data.events \
    --partitions 1 \
    --replication-factor 1 \
    --config retention.ms=$RETENTION_MS_30D \
    --config cleanup.policy=compact

  echo "All topics created successfully."
  kafka-topics --bootstrap-server $BOOTSTRAP_SERVER --list
  ```

- [ ] **2.3** Create `infra/kafka/Dockerfile`:
  ```dockerfile
  FROM confluentinc/cp-kafka:latest
  COPY create-topics.sh /usr/local/bin/create-topics.sh
  RUN chmod +x /usr/local/bin/create-topics.sh
  ENTRYPOINT ["/usr/local/bin/create-topics.sh"]
  ```

### Step 3: Add Kafka Init Container to Docker Compose

- [ ] **3.1** Add `kafka-init` service to `infra/docker/docker-compose.yml` right after the `kafka` service:
  ```yaml
  kafka-init:
    build:
      context: ./../kafka
      dockerfile: Dockerfile
    container_name: kafka-init
    command: ["kafka:9092"]
    depends_on:
      kafka:
        condition: service_healthy
    networks:
      - insurance-net
  ```

- [ ] **3.2** Add a comment block above the Kafka section explaining topic configuration:
  ```yaml
  # ============================================================
  # Kafka (SAGA events, domain events, audit, analytics)
  # Topics are created by the kafka-init container on first startup.
  # See: ../kafka/create-topics.sh for topic configuration.
  # ============================================================
  ```

### Step 4: Update Application Configurations

- [ ] **4.1** In each service's `application.yml`, ensure Kafka is configured properly:
  - Verify `spring.kafka.bootstrap-servers: localhost:9092`
  - Verify consumer `group-id` is unique per service
  - Verify `auto-offset-reset: earliest`
  - Remove any RabbitMQ configuration blocks

- [ ] **4.2** Update `spring.cloud.stream.kafka.binder.configuration` in each service to NOT rely on `dynamicDestinations` — topics should be pre-created by the init container:
  - Still keep `dynamicDestinations` for dev convenience, but add a comment noting topics are pre-provisioned

- [ ] **4.3** Standardize the Kafka binder configuration across all services. Each service's `application.yml` should have:
  ```yaml
  spring:
    kafka:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
      consumer:
        group-id: ${spring.application.name}-group
        auto-offset-reset: earliest
        key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
        value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
        properties:
          spring.json.trusted.packages: "com.insurancemanagementsystem.*"
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    cloud:
      stream:
        kafka:
          binder:
            brokers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
            configuration:
              auto.create.topics.enable: false  # topics are pre-provisioned
  ```
  Key change: `auto.create.topics.enable: false` — prevents accidental topic creation with wrong config.

### Step 5: Update Documentation

- [ ] **5.1** Update `docs/outlines/13_ENVIRONMENT_QUIRKS.md`:
  - Remove RabbitMQ port reference from "Message Broker Defaults" section
  - Add note about kafka-init container

- [ ] **5.2** Update `docs/outlines/12_DEVELOPER_COMMANDS.md`:
  - Remove RabbitMQ from "Order of Operations"
  - Add note: "Kafka topics are created automatically by kafka-init on `docker compose up`"

### Step 6: Verify

- [ ] **6.1** Start infrastructure: `docker compose -f infra/docker/docker-compose.yml up -d`
- [ ] **6.2** Wait for kafka-init container to complete (check logs: `docker logs kafka-init`)
- [ ] **6.3** Verify all topics are created: `docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list`
- [ ] **6.4** Verify topic configs: `docker exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic estimation.saga`
- [ ] **6.5** Verify no RabbitMQ container is running: `docker compose -f infra/docker/docker-compose.yml ps | grep -i rabbit` (should be empty)
- [ ] **6.6** Tear down: `docker compose -f infra/docker/docker-compose.yml down`

---

## Files to Create

| File | Purpose |
|------|---------|
| `infra/kafka/create-topics.sh` | Kafka topic creation script |
| `infra/kafka/Dockerfile` | Docker image for kafka-init container |

## Files to Modify

| File | Change |
|------|--------|
| `infra/docker/docker-compose.yml` | Remove RabbitMQ, add kafka-init, update comments |
| `.env.template` | Remove RabbitMQ variables |
| `docs/outlines/04_MESSAGE_QUEUE_TOPOLOGY.md` | Remove RabbitMQ sections, add DLQ topic spec |
| `docs/outlines/13_ENVIRONMENT_QUIRKS.md` | Remove RabbitMQ references |
| `docs/outlines/12_DEVELOPER_COMMANDS.md` | Remove RabbitMQ from instructions |
| `services/*/application.yml` (all services) | Standardize Kafka config, remove RabbitMQ |
| `services/reference-skeleton/...` | Remove RabbitMQ config (if present) |

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
- [ ] RabbitMQ completely removed from Docker Compose and all service configs
- [ ] `kafka-init` container creates all 7 topics on startup with correct configs
- [ ] `docker compose up -d` → all topics verified via `kafka-topics --list`
- [ ] All services build and start without RabbitMQ references
- [ ] Documentation updated (outlines, dev commands, env quirks)
