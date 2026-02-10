# Protobuf + Kafka Schema Registry Testbed

This project now includes:

1. Buf generation (`java`, `python`, `go`) from protobuf files.
2. Buf validation rules via `buf.validate` annotations.
3. Java Kafka producer using generated protobuf classes + Schema Registry.
4. Python Kafka producer using generated protobuf classes + Schema Registry.
5. Go Kafka producer using `confluent-kafka-go` + Schema Registry.
6. Docker Compose with:
   - Kafka (Confluent, KRaft, no ZooKeeper)
   - Confluent Schema Registry (enabled by default)
   - Apicurio Registry block (commented out, same host port `8081`)
   - Kafbat UI integrated with Kafka + Schema Registry
7. Topic record name strategy in all producers.

## Structure

- `proto/example/v1/customer_event.proto`: main event schema + references.
- `proto/example/v1/address.proto`, `proto/example/v1/event_metadata.proto`, `proto/example/v1/tenant.proto`: shared referenced message types.
- `buf.yaml`, `buf.gen.yaml`: buf lint/deps/generation config.
- `java-producer/generated/java`: generated Java code.
- `python-producer/generated/python`: generated Python code.
- `generated/go`: generated Go code.
- `java-producer/`: Gradle Java producer app.
- `python-producer/`: Python producer script + `uv`/`pyproject.toml`.
- `go-producer/`: Go producer using Confluent Kafka client and protobuf payloads.
- `docker-compose.yml`: local infrastructure stack.

## Prerequisites

- `buf` CLI installed and on `PATH`
- `docker` + `docker compose`
- Java 21 (for Gradle builds)
- Python 3.10+ (recommended)
- `uv` installed
- Go 1.22+ (you already have Go installed)
- `confluent-kafka-go` uses bundled `librdkafka` by default (no extra install required for this project)
- Optional (if you switch to dynamic linking): `brew install librdkafka pkg-config`

## Generate protobuf classes

```bash
make buf-deps
make buf-lint
make buf-generate
```

## Start local Kafka + Schema Registry + Kafbat

```bash
make up
```

- Kafka bootstrap from host: `localhost:29092`
- Schema Registry from host: `http://localhost:8081`
- Kafbat UI: `http://localhost:8080`

## Run Java producer

```bash
./gradlew :java-producer:run
```

Optional environment variables:

- `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:29092`)
- `SCHEMA_REGISTRY_URL` (default `http://localhost:8081`)
- `KAFKA_TOPIC` (default `customer-events`)

## Run Java consumer

```bash
./gradlew :java-producer:runConsumer
```

Optional environment variables:

- `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:29092`)
- `SCHEMA_REGISTRY_URL` (default `http://localhost:8081`)
- `KAFKA_TOPIC` (default `customer-events`)
- `KAFKA_GROUP_ID` (default `java-protobuf-consumer`)

## Run Python producer

```bash
cd python-producer
uv sync
uv run producer.py
```

Optional environment variables:

- `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:29092`)
- `SCHEMA_REGISTRY_URL` (default `http://localhost:8081`)
- `KAFKA_TOPIC` (default `customer-events`)

## Run Go producer

```bash
go mod tidy
go run ./go-producer
```

The Go producer now uses Confluent's Protobuf serializer (`confluent-kafka-go` schema-registry serde), and overrides subject naming to match Java's `TopicName-RecordName` (`customer-events-example.v1.EventEnvelope`).

Optional environment variables:

- `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:29092`)
- `SCHEMA_REGISTRY_URL` (default `http://localhost:8081`)
- `SCHEMA_REGISTRY_USERNAME` / `SCHEMA_REGISTRY_PASSWORD` (if auth is enabled)
- `SCHEMA_AUTO_REGISTER` (default `true`)
- `SCHEMA_NORMALIZE_SCHEMAS` (default `false`)
- `KAFKA_TOPIC` (default `customer-events`)
- `SEND_INTERVAL_SECONDS` (default `10`)

## Switching to Apicurio

`docker-compose.yml` currently enables Confluent Schema Registry.

To switch:

1. Comment out active `schema-registry` service (Confluent block).
2. Uncomment the Apicurio `schema-registry` block.
3. Restart the stack:

```bash
docker compose down
docker compose up -d
```

Both options use host port `8081`.
When using Apicurio with Confluent-compatible clients, set:
`SCHEMA_REGISTRY_URL=http://localhost:8081/apis/ccompat/v7`
