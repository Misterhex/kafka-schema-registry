# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Schema Registry Mirror is a wire-compatible Confluent Schema Registry implementation built with Spring Boot (3.4.1) and Java 21. It stores schemas in a compacted Kafka topic (`_schemas`) and materializes them in memory, exposing the same REST API as the Confluent Schema Registry.

## Build & Development Commands

```bash
# Build (skip tests)
./gradlew :server:build -x test

# Build bootable JAR
./gradlew :server:bootJar

# Run tests (requires Docker for Testcontainers)
./gradlew :server:test

# Run locally with Docker Compose (Kafka + Schema Registry)
docker compose up -d
docker compose logs -f schema-registry
docker compose down -v

# Run integration test suite (requires running Docker Compose stack)
./test.sh

# Build Docker image only
docker build -t schema-registry-mirror .

# Run JAR directly (requires Kafka at localhost:9092)
java -jar server/build/libs/schema-registry-mirror-0.1.0-SNAPSHOT.jar
```

Build uses Gradle 8.12 with the Spring Boot and Spring Dependency Management plugins. Dependencies include Confluent's schema registry client and schema providers (Avro, JSON Schema, Protobuf) from `packages.confluent.io/maven/`.

## Architecture

```
Controllers (REST API, port 8081)
        |
SchemaRegistryService (interface + impl)
        |
CompatibilityService (delegates to Confluent schema providers)
        |
KafkaSchemaStore (produces to / consumes from Kafka topic)
    /         \
InMemoryStore    KafkaStoreReaderThread
(ConcurrentHashMap)  (daemon, reads from beginning on startup)
```

**Event sourcing via Kafka:** All mutations are written to a compacted Kafka topic. On startup, `KafkaStoreReaderThread` replays the entire topic into `InMemoryStore` before the service begins accepting requests (controlled by a `CountDownLatch`). Reads are served from memory; writes go through Kafka then get consumed back.

**Storage model:** The Kafka topic stores typed key/value pairs serialized as JSON. Key types are `SchemaKey`, `ConfigKey`, `ModeKey`, `DeleteSubjectKey`, `ClearSubjectKey`, and `NoopKey` (see `storage/model/`). Each key type corresponds to a different category of metadata.

**Key design constraints:**
- Single partition (partition 0) for total ordering
- Idempotent producer with `max.in.flight.requests = 1` and `acks = all`
- Schema deduplication via `putIfAbsent` — first registration wins
- Two-phase delete: soft delete marker, then permanent delete

## Package Structure

All code lives under `io.schemaregistry.mirror`:

- `config/` — Spring configuration: Jackson, Kafka clients, properties binding (`schema.registry.*`), content negotiation for `application/vnd.schemaregistry.v1+json`
- `controller/` — REST controllers for subjects, versions, schemas, compatibility, config, mode, contexts, and server metadata
- `service/` — `SchemaRegistryService` interface and `SchemaRegistryServiceImpl` (core business logic), `CompatibilityService`
- `storage/` — `KafkaSchemaStore`, `InMemoryStore`, `KafkaStoreReaderThread`, and `model/` subdirectory with all key/value types
- `exception/` — `SchemaRegistryException` with Confluent-compatible error codes, `GlobalExceptionHandler`
- `schema/` — `CompatibilityLevel` enum (NONE, BACKWARD, FORWARD, FULL, and their TRANSITIVE variants)

## A/B Testing Module

The `ab-testing/` subproject is a CLI application that validates the Mirror implementation against the official Confluent Schema Registry. It sends identical requests to both instances and compares responses across 18 test phases (registration, evolution, compatibility, deletes, error codes, protobuf, references, cleanup, etc.).

```bash
# Build
./gradlew :ab-testing:build -x test

# Start both registries for A/B testing
docker compose -f docker-compose-ab-test.yml up -d

# Run A/B tests
./gradlew :ab-testing:bootRun

# Or via JAR
java -jar ab-testing/build/libs/ab-testing-0.1.0-SNAPSHOT.jar
```

**Configuration** (env vars or Spring properties):
- `ABTEST_CONFLUENT_URL` — Confluent SR endpoint (default: `http://localhost:8085`)
- `ABTEST_MIRROR_URL` — Mirror SR endpoint (default: `http://localhost:8086`)
- `ABTEST_MIRROR_USERNAME` / `ABTEST_MIRROR_PASSWORD` — Auth credentials for mirror
- `ABTEST_REPORT_FILE` — Output report path (default: `ab-test-report.json`)

**Key classes** under `io.schemaregistry.abtest`:
- `AbTestRunner` orchestrates all test phases and collects results
- `HttpExecutor` sends identical requests to both registries
- `ResponseComparator` compares responses using three modes: `EXACT`, `SET` (order-independent), `STRUCTURAL` (ignoring transient fields like timestamps)
- Test phases are in `tests/` — each implements `TestPhase` via `AbstractTestPhase`

Exit code 0 = all tests passed, 1 = differences found.

## Testing

- **Unit/integration tests:** JUnit 5 via `./gradlew :server:test`. Uses Testcontainers (`org.testcontainers:kafka`) so Docker must be running.
- **Bash integration tests:** `test.sh` exercises the full REST API with curl against a running instance. Set `SR_URL` to override the default `http://localhost:8081`.
- **A/B tests:** See "A/B Testing Module" section above.

## Configuration

Spring Boot config is in `server/src/main/resources/application.yml`. All properties are under the `schema.registry` prefix and bound to `SchemaRegistryProperties`. Override via environment variables (e.g., `SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS`, `SCHEMA_REGISTRY_COMPATIBILITY_LEVEL`). See the README for the full list.

## Error Handling

`SchemaRegistryException` carries a Confluent-compatible numeric error code (e.g., 40401 = subject not found, 42201 = invalid schema). `GlobalExceptionHandler` translates these to JSON responses with `error_code` and `message` fields. Error codes are defined as constants in `SchemaRegistryException`.
