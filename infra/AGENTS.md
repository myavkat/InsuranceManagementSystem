# Infrastructure AGENTS.md

## Overview
Infrastructure artifacts including Docker Compose, SQL init scripts, and Kafka configurations. 

## Workflow Commands
Building and Testing:
1. Start infrastructure (PostgreSQL, Kafka):
   ```bash
   docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.override.yml up -d
   ```
2. Stop infrastructure:
   ```bash
   docker compose -f infra/docker/docker-compose.yml down
   ```

## Environment Variables
Every `${ENV_VAR:default}` placeholder referenced in any service's `application.yml` MUST have a corresponding entry in the root `.env.template` file. The `.env.template` file is the single source of truth for all configurable environment variables.

## Message Queue Topology
- **Single Broker**: Kafka is used exclusively for SAGA events, domain events, audit, analytics, and RPC.
- **Topics**: `estimation.saga` (SAGA events), `dlq.saga` (dead-letter queue), and per-service domain event topics (`customer.events`, `vehicle.events`, etc.).
- **Pre-provisioned**: Topics are pre-provisioned by the `kafka-init` container (`infra/kafka/create-topics.sh`). `auto.create.topics.enable: false` is set to prevent accidental topic creation.

## Port Allocation
- `customer-service`: 8081
- `vehicle-service`: 8082
- `realestate-service`: 8083
- `insurance-service`: 8084
- `estimation-service`: 8085
- `reference-data-service`: 8086
- `auth-service`: 8087
- `api-gateway`: 8080
- Next.js frontend: 3000
- Zipkin: 9411
- PostgreSQL: 5432
- Kafka: 9092
