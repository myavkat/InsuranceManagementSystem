# Message Queue Topology Outline

## Division of Labor

| Broker | Purpose | Characteristics |
|--------|---------|-----------------|
| **Kafka** | SAGA events, domain events, audit, analytics | Durable, replayable, log-compacted for entity state |
| **RabbitMQ** | Synchronous RPC calls, dead-letter handling | Request/response pattern, immediate delivery |

---

## Kafka Topics

### SAGA Topic

| Topic | Partitions | Retention | Compaction | Description |
|-------|-----------|-----------|------------|-------------|
| `estimation.saga` | 3 | 7 days | No | All SAGA workflow events |

**Producers/Consumers:** See [03_SAGA_PATTERN.md](./03_SAGA_PATTERN.md).

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

## RabbitMQ

### RPC Queue

| Queue | Exchange | Routing Key | Description |
|-------|----------|-------------|-------------|
| `rpc.reference-data` | `rpc-exchange` (direct) | `reference-data.getCities`, `reference-data.getProfessions` | Synchronous lookup for cities/professions |

**Pattern:** Services publish a request message with a `replyTo` queue and `correlationId`. The Reference Data Service processes the request and publishes the response to the `replyTo` queue. Consumers wait with a timeout.

### Dead-Letter Queue

| Queue | Source | Description |
|-------|--------|-------------|
| `dlq.saga` | `estimation.saga` (RabbitMQ bridge, if used) | Failed SAGA message processing |

---

## Configuration Per Service

Each service that communicates via message brokers declares in `application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: <service-name>-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.insurancemanagementsystem.*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

rabbitmq:
  host: localhost
  port: 5672
  username: guest
  password: guest
```
