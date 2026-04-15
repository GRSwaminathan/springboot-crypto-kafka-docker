# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A multi-module Maven project that streams live cryptocurrency price data from the Bitstamp REST API through Apache Kafka (KRaft mode, no ZooKeeper), with full OpenTelemetry observability piped to Loki and Grafana.

- **Stack**: Java 25, Spring Boot 3.4.4, Apache Kafka 7.6.1 (Confluent), Docker Compose
- **Modules**: `kafka-docker-producer` (fetches crypto data, publishes to Kafka) and `kafka-docker-consumer` (subscribes and logs prices)

## Build Commands

```bash
# Build all modules from repo root
./mvnw clean package

# Build Docker images (run after mvn package)
./mvnw docker:build -pl kafka-docker-producer
./mvnw docker:build -pl kafka-docker-consumer

# Run a single test class
./mvnw test -pl kafka-docker-consumer -Dtest=KafkaDockerConsumerApplicationTests
./mvnw test -pl kafka-docker-producer -Dtest=KafkaDockerProducerApplicationTests

# Start all services (Kafka, UI, observability stack, producer, consumers)
docker-compose up

# Rebuild and restart containers after code changes
docker-compose up --build
```

## Architecture

### Data Flow

```
Bitstamp REST API
      │  (HTTP, every 3 seconds)
      ▼
kafka-docker-producer  ──→  Kafka topic (BTC / LTC / ETH / BCH / XRP)
                                    │
                             kafka-docker-consumer (one per topic group)
                                    │
                             OTEL Collector ──→ Loki ──→ Grafana
```

### Kafka Client Style

Both modules use the **low-level Apache Kafka client** (`KafkaProducer` / `KafkaConsumer`), not Spring's `KafkaTemplate` or `@KafkaListener`. CLI arguments control bootstrap servers, topics, consumer group IDs, and polling intervals at startup — no Spring bean wiring for Kafka.

Producer startup args: `<bootstrap-server> <initial_delay_ms> <interval_ms> <topics>`  
Consumer startup args: `<bootstrap-server> <group_id> <topics>`  
Example: `localhost:29092 1000 3000 BTC,LTC,ETH`

### Topic-per-Cryptocurrency Model

`CryptoType.java` (producer module) is the single source of truth for supported coins: **BTC, LTC, BCH, XRP, ETH**. Each enum value holds the coin's Bitstamp ticker URL and short name (used as both the Kafka topic name and the message key). Adding a new coin means adding an enum entry here.

### Message Format

Messages are raw JSON strings from the Bitstamp `ticker_hour` endpoint. The consumer extracts the `"last"` field via Jackson `ObjectMapper` to get the current price. No custom Kafka serializers — everything is `String`/`String`.

### Observability

Both modules push traces and logs to the OTel Collector over OTLP HTTP (`otel-collector:4318`). The collector forwards to Loki. Sampling is set to 100% (`management.tracing.sampling.probability=1.0`). `logback-spring.xml` configures the `OpenTelemetryAppender`.

- Kafka UI: http://localhost:8085
- Grafana: http://localhost:3000 (anonymous admin access)
- Loki: http://localhost:3100

### Docker Compose Services

| Service | Purpose |
|---------|---------|
| `kafka` | Single-broker KRaft cluster (ports 9092/29092) |
| `kafka-ui` | Web UI for topic/partition/consumer-lag inspection |
| `producer` | Fetches Bitstamp prices and publishes to Kafka |
| `consumer-btc` | Consumes BTC topic |
| `consumer-ltc` | Consumes LTC topic |
| `otel-collector` | Receives OTLP, exports to Loki |
| `loki` | Log aggregation |
| `grafana` | Dashboards |

Producer and consumer Docker images are built via the Spotify Docker Maven Plugin and pushed to the local daemon. Base image is `eclipse-temurin:25-jre-alpine`.
