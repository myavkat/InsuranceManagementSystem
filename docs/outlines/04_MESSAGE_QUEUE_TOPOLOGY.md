# Message Queue Topology Outline

## Single Broker Architecture

All inter-service communication uses **Kafka exclusively** — SAGA events, domain events, and RPC-style request/reply all flow through Kafka topics.

| Broker | Purpose | Characteristics |
|--------|---------|-----------------|
| **Kafka** | SAGA events, domain events, audit, analytics, RPC | Durable, replayable, log-compacted for entity state |

---

## Kafka Topics

### SAGA Topic

| Topic | Partitions | Retention | Compaction | Description |
|-------|-----------|-----------|------------|-------------|
| `estimation.saga` | 3 | 7 days | No | All SAGA workflow events |

**Producers/Consumers:** See [03_SAGA_PATTERN.md](./03_SAGA_PATTERN.md).

### Dead-Letter Queue Topic

| Topic | Partitions | Retention | Compaction | Description |
|-------|-----------|-----------|------------|-------------|
| `dlq.saga` | 1 | 30 days | No (delete) | Failed SAGA event processing |

### Domain Event Topics

| Topic | Partitions | Retention | Compaction | Description |
|-------|-----------|-----------|------------|-------------|
| `customer.events` | 2 | 30 days | Yes (key: customerId) | Customer created/updated/deleted |
| `vehicle.events` | 2 | 30 days | Yes (key: vehicleId) | Vehicle created/updated/deleted |
| `realestate.events` | 2 | 30 days | Yes (key: realEstateId) | Real estate created/updated/deleted |
| `insurance.events` | 2 | 30 days | Yes (key: insuranceId) | Insurance product changes |
| `reference-data.events` | 1 | 30 days | Yes (key: entity type) | Reference data changes |

**Purpose of domain event topics:** Audit trail, analytics, cache invalidation, eventual consistency across services. Log-compacted topics allow new consumers to rebuild current state.

---

## Configuration Per Service

Each service that communicates via message brokers declares in `application.yml`:

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

> **Note:** Topics are pre-provisioned by the `kafka-init` container (see `infra/kafka/create-topics.sh`). The `auto.create.topics.enable: false` setting prevents accidental topic creation with wrong configuration.
