# Protobuf + Kafka Schema Registry Testbed

This project now includes:

1. Buf generation (`java`, `python`, `go`) from protobuf files.
2. Buf validation rules via `buf.validate` annotations.
3. Java Kafka producer using generated protobuf classes + Schema Registry.
4. Python Kafka producer using generated protobuf classes + Schema Registry.
5. Docker Compose with:
   - Kafka (Confluent, KRaft, no ZooKeeper)
   - Confluent Schema Registry (enabled by default)
   - Apicurio Registry block (commented out, same host port `8081`)
   - Kafbat UI integrated with Kafka + Schema Registry
6. Topic record name strategy in both producers.

## Structure

- `proto/example/v1/customer_event.proto`: main event schema + references.
- `proto/example/v1/address.proto`, `proto/example/v1/event_metadata.proto`, `proto/example/v1/tenant.proto`: shared referenced message types.
- `buf.yaml`, `buf.gen.yaml`: buf lint/deps/generation config.
- `java-producer/generated/java`: generated Java code.
- `python-producer/generated/python`: generated Python code.
- `generated/go`: generated Go code.
- `java-producer/`: Gradle Java producer app.
- `python-producer/`: Python producer script + `uv`/`pyproject.toml`.
- `docker-compose.yml`: local infrastructure stack.

## Prerequisites

- `buf` CLI installed and on `PATH`
- `docker` + `docker compose`
- Java 21 (for Gradle builds)
- Python 3.10+ (recommended)
- `uv` installed

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
