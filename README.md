# springboot-crypto-kafka-docker

A Spring Boot + Kafka project that streams real-time crypto currency prices using [Confluent Kafka](https://www.confluent.io/) in KRaft mode (no ZooKeeper), all orchestrated with [Docker Compose](https://docs.docker.com/compose/).

It starts one Producer and two Consumer containers to emulate message publish/consume using crypto currency JSON data from the [Bitstamp REST API](https://www.bitstamp.net/api/) (Bitcoin, Litecoin, etc.). The Producer and Consumer images are built with [Spring Boot](https://spring.io/projects/spring-boot).

---

## Tech Stack

| Component        | Version / Image                          |
|------------------|------------------------------------------|
| Java             | 25                                       |
| Spring Boot      | 3.4.4                                    |
| Kafka            | Confluent 7.6.1 (KRaft mode)             |
| Kafka UI         | provectuslabs/kafka-ui                   |
| OTel Collector   | otel/opentelemetry-collector-contrib     |
| Loki             | grafana/loki                             |
| Grafana          | grafana/grafana                          |

---

## Services

| Service          | Port  | Description                              |
|------------------|-------|------------------------------------------|
| Kafka            | 29092 | Kafka broker (KRaft, no ZooKeeper)       |
| Kafka UI         | 8085  | Browse topics, partitions, consumer lag  |
| OTel Collector   | 4317/4318 | Receives OTLP logs and traces        |
| Loki             | 3100  | Log aggregation backend                  |
| Grafana          | 3000  | Dashboards and log exploration           |

---

## Observability (OpenTelemetry)

All application logs are shipped to **Grafana Loki** via the **OpenTelemetry Collector** using the OTLP protocol.

```
Spring Boot Apps → OTLP (HTTP :4318) → OTel Collector → Grafana Loki → Grafana
```

Each service exports logs and traces with a distinct `OTEL_SERVICE_NAME`:
- `kafka-producer`
- `kafka-consumer-btc`
- `kafka-consumer-ltc`

### Viewing logs in Grafana

Loki is pre-configured as the default datasource and a **Crypto Kafka Logs** dashboard is provisioned automatically on startup — no manual setup required.

1. Open `http://localhost:3000`
2. Go to **Dashboards → Crypto Kafka Logs**

The dashboard has four panels:

| Panel | LogQL query |
|-------|-------------|
| All Service Logs | `{service_name=~"kafka-.+"}` |
| Producer | `{service_name="kafka-producer"}` |
| Consumer BTC | `{service_name="kafka-consumer-btc"}` |
| Consumer LTC | `{service_name="kafka-consumer-ltc"}` |

All panels auto-refresh every 5 seconds and default to the last 15 minutes.

To explore further, open **Explore** and run custom LogQL queries:
```
{service_name="kafka-producer"}
{service_name="kafka-consumer-btc"} |= "BTC"
```

---

## Prerequisites

- Docker & Docker Compose
- Java 25
- Maven 3.9+

---

## Build & Run

```bash
make up      # build JARs, build Docker images, start all services
make down    # stop all services
make rebuild # rebuild and hot-swap only producer/consumer containers
make logs    # tail logs for producer and consumers
```

Or run the steps manually:

```bash
# From project root
./mvnw clean package -DskipTests
./mvnw docker:build -pl kafka-docker-producer
./mvnw docker:build -pl kafka-docker-consumer
docker-compose up -d
```

---

## Project Structure

```
springboot-crypto-kafka-docker/
├── kafka-docker-producer/        # Spring Boot Kafka producer
├── kafka-docker-consumer/        # Spring Boot Kafka consumer
├── docker-compose.yml            # All services
├── otel-collector-config.yml     # OpenTelemetry Collector pipeline config
└── pom.xml                       # Parent POM
```

---

## Debugging

### Check if all containers are running

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

### Verify logs are reaching the OTel Collector

```bash
docker logs otel-collector 2>&1 | tail -20
```

Look for `"log records": N` in the output. If absent, logs are not being received.

### Verify logs are reaching Loki

```bash
# Should list label names (e.g. service_name) when logs are present
curl http://localhost:3100/loki/api/v1/labels

# Query logs for the last hour
curl -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={service_name=~".+"}' \
  --data-urlencode "start=$(date -v-1H +%s)000000000" \
  --data-urlencode "end=$(date +%s)000000000"
```

### Check producer / consumer application logs

```bash
docker logs producer 2>&1 | tail -40
docker logs consumer-btc 2>&1 | tail -40
docker logs consumer-ltc 2>&1 | tail -40
```

### Common issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| `otel-collector` exits immediately | Unknown exporter type in config | Check `otel-collector-config.yml` — use `otlp_http` (not `otlphttp`) |
| `curl /loki/api/v1/labels` returns `{}` | No logs have reached Loki yet | Confirm OTel Collector shows `log records > 0`; wait for next poll cycle |
| Loki query error "not supported as instant query" | Used `/query` endpoint for log queries | Use `/query_range` with `start` and `end` params |
| `OpenTelemetryAppender` not exporting | Appender not installed at startup | Ensure `OpenTelemetryAppender.install(openTelemetry)` is called in `@EventListener(ApplicationStartedEvent.class)` |
| Stale Docker images after code changes | Old image still in use | Rebuild with `mvn clean package` + `mvn docker:build`, then `docker-compose up -d --force-recreate` |
| Consumer logs all appear under `kafka-consumer` in Loki | Spring Boot uses `spring.application.name` (not `OTEL_SERVICE_NAME`) for the OTel service name | Set `SPRING_APPLICATION_NAME` per container in `docker-compose.yml` |

---

## Original Article

### [SpringBoot-Kafka Integration: Dynamic Crypto Price Tracking in Real Time](https://medium.com/gitconnected/springboot-kafka-integration-dynamic-crypto-price-tracking-in-real-time-948ed8990744)
_Learn how to leverage Kafka's distributed streaming platform to get up-to-date crypto prices for your application_
