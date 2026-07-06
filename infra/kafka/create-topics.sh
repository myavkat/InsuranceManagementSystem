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

# DLQ topic — 1 partition, 30-day retention, no compaction
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
