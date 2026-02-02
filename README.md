# Schema Registry Mirror

A wire-compatible [Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html) implementation built with Spring Boot 3.4.1 and Java 21. It stores schemas in a compacted Kafka topic (`_schemas`) and materializes them in memory, providing the same REST API as the Confluent Schema Registry. Supports AVRO, JSON, and PROTOBUF schema types.

## Prerequisites

| Dependency | Version | Notes |
|---|---|---|
| Java | 21 | Eclipse Temurin recommended |
| Gradle | 8.12 | Bundled via `gradlew` wrapper |
| Docker | 20+ | Required for Testcontainers and Docker Compose |
| Apache Kafka | 3.8+ | KRaft mode (no ZooKeeper) |

## Quick Start

### Using Docker Compose

```bash
docker compose up -d
```

This starts Kafka (KRaft mode, no ZooKeeper) and the schema registry on port 8081.

### Running the JAR Directly

Requires a running Kafka broker.

```bash
./gradlew :server:bootJar
java -jar server/build/libs/schema-registry-mirror-0.1.0-SNAPSHOT.jar
```

### Verify It Works

```bash
curl http://localhost:8081/schemas/types
# ["AVRO","JSON","PROTOBUF"]
```

## Configuration

All settings are defined in `server/src/main/resources/application.yml` and can be overridden via environment variables:

| Environment Variable | Default | Description |
|---|---|---|
| `SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `SCHEMA_REGISTRY_KAFKASTORE_TOPIC` | `_schemas` | Kafka topic for schema storage |
| `SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR` | `1` | Replication factor for the schemas topic |
| `SCHEMA_REGISTRY_GROUP_ID` | `schema-registry-mirror` | Kafka consumer group ID |
| `SCHEMA_REGISTRY_COMPATIBILITY_LEVEL` | `BACKWARD` | Default global compatibility level |
| `SCHEMA_REGISTRY_MODE` | `READWRITE` | Default global mode |
| `SCHEMA_REGISTRY_HOST` | `localhost` | Advertised host |
| `SCHEMA_REGISTRY_INIT_TIMEOUT` | `60000` | Initialization timeout (ms) |
| `SCHEMA_REGISTRY_KAFKASTORE_TIMEOUT` | `500` | Kafka store operation timeout (ms) |

The server listens on port `8081` (configured via `server.port` in `application.yml`).

Spring Boot Actuator exposes `/actuator/health` and `/actuator/info` endpoints for health checking and service metadata.

## How It Works

Schema Registry Mirror uses an event-sourcing architecture with a compacted Kafka topic as the source of truth and an in-memory store for serving reads.

### Data Flow

```
                     ┌─────────────────────────────────────────────────┐
                     │              Schema Registry Mirror             │
                     │                                                 │
  REST Request ─────►│  Controller ──► Service ──► KafkaSchemaStore    │
                     │                                    │            │
                     │                                    │ produce    │
                     │                                    ▼            │
                     │                            ┌──────────────┐     │
                     │                            │ Kafka Topic  │     │
                     │                            │  (_schemas)  │     │
                     │                            └──────┬───────┘     │
                     │                                   │ consume     │
                     │                                   ▼            │
                     │                        KafkaStoreReaderThread   │
                     │                                   │            │
                     │                                   │ apply      │
                     │                                   ▼            │
  REST Response ◄────│  Controller ◄── Service ◄── InMemoryStore      │
                     │                                                 │
                     └─────────────────────────────────────────────────┘
```

### Write Path

All mutations (register schema, update config, delete subject, etc.) are produced to the `_schemas` Kafka topic by `KafkaSchemaStore`. The write is not considered complete until the record is acknowledged by Kafka.

### Read Path

All reads are served directly from `InMemoryStore`, which holds the fully materialized state in memory. This provides low-latency responses without any Kafka or database round-trips.

### Startup Flow

1. The application starts and creates a `KafkaStoreReaderThread` (a background daemon thread).
2. The reader thread consumes the `_schemas` topic from the beginning.
3. Each record is deserialized and applied to `InMemoryStore`, rebuilding the full state.
4. Once the reader catches up to the end of the topic, the registry is ready to serve requests.
5. The reader continues to consume new records in the background, keeping the in-memory state current.

### Key Invariants

- **All writes go through Kafka first.** The in-memory store is never written to directly by the service layer.
- **Durability via Kafka.** Schemas survive restarts because state is rebuilt from the compacted topic.
- **Consistency via total ordering.** Kafka provides a single-partition total order for all schema operations.

## Project Structure

```
server/src/main/java/io/schemaregistry/mirror/
├── SchemaRegistryMirrorApplication.java   # Application entry point
├── config/                                # Spring configuration
│   ├── SchemaRegistryProperties.java      # Binds schema.registry.* properties
│   ├── KafkaConfig.java                   # Kafka producer and consumer beans
│   ├── JacksonConfig.java                 # JSON serialization settings
│   └── WebMvcConfig.java                  # Content negotiation, media types
├── controller/                            # REST API layer (9 controllers)
│   ├── RootController.java                # GET /
│   ├── SubjectsController.java            # /subjects
│   ├── SubjectVersionsController.java     # /subjects/{subject}/versions
│   ├── SchemasController.java             # /schemas/ids/{id}
│   ├── CompatibilityController.java       # /compatibility/subjects/{subject}/versions
│   ├── ConfigController.java              # /config
│   ├── ModeController.java                # /mode
│   ├── ContextsController.java            # /contexts
│   └── ServerMetadataController.java      # /v1/metadata
├── service/                               # Business logic
│   ├── SchemaRegistryService.java         # Service interface
│   ├── SchemaRegistryServiceImpl.java     # Core implementation (~580 lines)
│   └── CompatibilityService.java          # Schema compatibility checking
├── storage/                               # Persistence layer
│   ├── SchemaStore.java                   # Store interface
│   ├── KafkaSchemaStore.java              # Kafka producer (writes)
│   ├── KafkaStoreReaderThread.java        # Kafka consumer (reads → memory)
│   ├── InMemoryStore.java                 # In-memory materialized state
│   └── model/                             # Kafka topic record types
│       ├── SchemaRegistryKey.java         # Base key interface
│       ├── SchemaRegistryValue.java       # Base value interface
│       ├── SchemaRegistryKeyType.java     # Key type enum
│       ├── SchemaKey.java / SchemaValue.java
│       ├── ConfigKey.java / ConfigValue.java
│       ├── ModeKey.java / ModeValue.java
│       ├── DeleteSubjectKey.java / DeleteSubjectValue.java
│       ├── ClearSubjectKey.java / ClearSubjectValue.java
│       └── NoopKey.java
├── schema/                                # Schema types
│   └── CompatibilityLevel.java            # Enum: NONE, BACKWARD, FORWARD, FULL, *_TRANSITIVE
└── exception/                             # Error handling
    ├── SchemaRegistryException.java       # Confluent-compatible error codes
    └── GlobalExceptionHandler.java        # Maps exceptions to REST responses
```

### Package Descriptions

**`config/`** — Spring configuration beans. `SchemaRegistryProperties` binds all `schema.registry.*` properties from `application.yml`. `KafkaConfig` creates the Kafka producer and consumer. `JacksonConfig` configures JSON serialization. `WebMvcConfig` sets up content negotiation and registers Confluent-compatible media types (`application/vnd.schemaregistry.v1+json`).

**`controller/`** — Nine REST controllers that map the full Confluent Schema Registry API surface (29 endpoints). All endpoints produce `application/vnd.schemaregistry.v1+json`. Controllers delegate to the service layer and do not contain business logic.

**`service/`** — `SchemaRegistryServiceImpl` contains the core business logic for schema registration, lookup, deletion, compatibility checking, and config/mode management. `CompatibilityService` uses Confluent's schema providers (Avro, JSON Schema, Protobuf) to evaluate schema compatibility.

**`storage/`** — Dual-layer persistence. `KafkaSchemaStore` writes typed records to Kafka. `KafkaStoreReaderThread` is a background daemon that consumes from the `_schemas` topic and applies records to `InMemoryStore`. The `model/` subdirectory contains typed key/value pairs serialized as JSON in the Kafka topic.

**`exception/`** — `SchemaRegistryException` carries Confluent-compatible error codes (40401, 42201, etc.). `GlobalExceptionHandler` is a `@RestControllerAdvice` that translates exceptions into JSON error responses.

**`schema/`** — Contains the `CompatibilityLevel` enum with seven levels: `NONE`, `BACKWARD`, `BACKWARD_TRANSITIVE`, `FORWARD`, `FORWARD_TRANSITIVE`, `FULL`, `FULL_TRANSITIVE`.

## Entrypoints

### Application Main Class

`io.schemaregistry.mirror.SchemaRegistryMirrorApplication` — annotated with `@SpringBootApplication` and `@EnableConfigurationProperties`. This is the standard Spring Boot entry point.

### REST API

29 endpoints across 9 controllers, listening on port 8081. All responses use `application/vnd.schemaregistry.v1+json` content type. See [API Reference](#api-reference) below.

### Kafka Consumer

`KafkaStoreReaderThread` runs as a background daemon thread. It consumes the `_schemas` compacted topic from the beginning, materializing all records into `InMemoryStore`. It continues running for the lifetime of the application to pick up new writes.

### Actuator Endpoints

Spring Boot Actuator is enabled with the following endpoints:

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Application health status |
| `GET /actuator/info` | Application info |

## API Reference

All endpoints produce `application/vnd.schemaregistry.v1+json`.

### Root

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Returns empty JSON object `{}` |

### Subjects

| Method | Path | Query Params | Description |
|---|---|---|---|
| `GET` | `/subjects` | `subjectPrefix`, `deleted` | List all subjects |
| `POST` | `/subjects/{subject}` | `normalize`, `deleted` | Look up schema under subject |
| `DELETE` | `/subjects/{subject}` | `permanent` | Delete subject (soft or permanent) |

### Versions

| Method | Path | Query Params | Description |
|---|---|---|---|
| `GET` | `/subjects/{subject}/versions` | `deleted` | List versions for a subject |
| `GET` | `/subjects/{subject}/versions/{version}` | `deleted` | Get schema by version |
| `GET` | `/subjects/{subject}/versions/{version}/schema` | | Get raw schema string by version |
| `GET` | `/subjects/{subject}/versions/{version}/referencedby` | | Get schemas that reference this version |
| `POST` | `/subjects/{subject}/versions` | `normalize` | Register a new schema |
| `DELETE` | `/subjects/{subject}/versions/{version}` | `permanent` | Delete a schema version |

The `{version}` parameter accepts an integer or the string `latest`.

### Schemas

| Method | Path | Query Params | Description |
|---|---|---|---|
| `GET` | `/schemas/ids/{id}` | `subject`, `fetchMaxId` | Get schema by global ID |
| `GET` | `/schemas/ids/{id}/schema` | `subject` | Get raw schema string by global ID |
| `GET` | `/schemas/ids/{id}/subjects` | `deleted` | Get subjects associated with a schema ID |
| `GET` | `/schemas/ids/{id}/versions` | `deleted` | Get subject-version pairs for a schema ID |
| `GET` | `/schemas/types` | | List supported schema types (`AVRO`, `JSON`, `PROTOBUF`) |

### Compatibility

| Method | Path | Query Params | Description |
|---|---|---|---|
| `POST` | `/compatibility/subjects/{subject}/versions/{version}` | `verbose` | Test schema compatibility against a specific version |
| `POST` | `/compatibility/subjects/{subject}/versions` | `verbose` | Test schema compatibility against all versions |

**Request body:**
```json
{
  "schema": "{\"type\":\"record\",\"name\":\"Test\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}",
  "schemaType": "AVRO"
}
```

**Response:**
```json
{"is_compatible": true}
```

### Config

| Method | Path | Query Params | Description |
|---|---|---|---|
| `GET` | `/config` | | Get global compatibility level |
| `PUT` | `/config` | | Set global compatibility level |
| `DELETE` | `/config` | | Delete global compatibility override (reset to default) |
| `GET` | `/config/{subject}` | `defaultToGlobal` | Get subject compatibility level |
| `PUT` | `/config/{subject}` | | Set subject compatibility level |
| `DELETE` | `/config/{subject}` | | Delete subject compatibility override |

**Request body (PUT):**
```json
{"compatibility": "BACKWARD"}
```

Valid levels: `NONE`, `BACKWARD`, `FORWARD`, `FULL`, `BACKWARD_TRANSITIVE`, `FORWARD_TRANSITIVE`, `FULL_TRANSITIVE`.

### Mode

| Method | Path | Query Params | Description |
|---|---|---|---|
| `GET` | `/mode` | | Get global mode |
| `PUT` | `/mode` | `force` | Set global mode |
| `DELETE` | `/mode` | | Delete global mode override |
| `GET` | `/mode/{subject}` | `defaultToGlobal` | Get subject mode |
| `PUT` | `/mode/{subject}` | `force` | Set subject mode |
| `DELETE` | `/mode/{subject}` | | Delete subject mode override |

**Request body (PUT):**
```json
{"mode": "READWRITE"}
```

Valid modes: `READWRITE`, `READONLY`, `READONLY_OVERRIDE`, `IMPORT`.

### Contexts

| Method | Path | Description |
|---|---|---|
| `GET` | `/contexts` | List contexts (returns `["."]`) |

### Server Metadata

| Method | Path | Description |
|---|---|---|
| `GET` | `/v1/metadata/id` | Get Kafka cluster ID |
| `GET` | `/v1/metadata/version` | Get server version |

## Error Codes

All error responses use the format:
```json
{"error_code": 40401, "message": "Subject 'my-subject' not found."}
```

| Error Code | HTTP Status | Description |
|---|---|---|
| 40401 | 404 | Subject not found |
| 40402 | 404 | Version not found |
| 40403 | 404 | Schema not found |
| 40404 | 404 | Subject was soft deleted |
| 40405 | 404 | Subject was not soft deleted before permanent delete |
| 40406 | 404 | Schema version was soft deleted |
| 40407 | 404 | Schema version was not soft deleted before permanent delete |
| 40408 | 404 | Subject-level compatibility not configured |
| 40409 | 404 | Subject-level mode not configured |
| 40901 | 409 | Incompatible schema |
| 42201 | 422 | Invalid schema |
| 42202 | 422 | Invalid version |
| 42203 | 422 | Invalid compatibility level |
| 42204 | 422 | Invalid mode |
| 42205 | 422 | Operation not permitted |
| 42206 | 422 | Reference exists |
| 42207 | 422 | ID does not match |
| 42208 | 422 | Invalid subject |
| 50001 | 500 | Store error |
| 50002 | 500 | Operation timeout |
| 50003 | 500 | Request forwarding failed |
| 50004 | 500 | Unknown leader |

## Building

Build the project (without tests):

```bash
./gradlew :server:build -x test
```

Build only the fat JAR:

```bash
./gradlew :server:bootJar
```

The JAR is output to `server/build/libs/schema-registry-mirror-0.1.0-SNAPSHOT.jar`.

Build the Docker image:

```bash
docker build -t schema-registry-mirror .
```

The Dockerfile uses a multi-stage build: `gradle:8.12-jdk21` for building, `eclipse-temurin:21-jre-alpine` for the runtime image.

## Testing

### Unit and Integration Tests (Gradle)

Requires Docker (for Testcontainers with Kafka):

```bash
./gradlew :server:test
```

Run a single test class:

```bash
./gradlew :server:test --tests "io.schemaregistry.mirror.integration.SomeTest"
```

### Shell Integration Tests

`test.sh` runs 32 curl-based assertions covering the full API surface. Requires the service to be running.

```bash
# Start the service
docker compose up -d

# Run the tests (default: http://localhost:8081)
./test.sh

# Override the target URL
SR_URL=http://host:port ./test.sh

# Clean up
docker compose down -v
```

The test script covers: root endpoint, subject CRUD, schema registration (AVRO, JSON, PROTOBUF), versioning, schema lookup by ID and content, compatibility checking, config and mode management, soft and permanent deletion, error cases, content-type validation, and server metadata.

## Running

### JAR

Requires a running Kafka broker at the configured bootstrap servers.

```bash
./gradlew :server:bootJar
java -jar server/build/libs/schema-registry-mirror-0.1.0-SNAPSHOT.jar
```

Override configuration via environment variables:

```bash
SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092 \
  java -jar server/build/libs/schema-registry-mirror-0.1.0-SNAPSHOT.jar
```

### Docker

```bash
docker build -t schema-registry-mirror .
docker run -p 8081:8081 \
  -e SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS=kafka:9092 \
  schema-registry-mirror
```

### Docker Compose

Starts Kafka (KRaft mode, Apache Kafka 4.0.0) and the schema registry together:

```bash
docker compose up -d        # Start all services
docker compose logs -f       # View logs
docker compose down -v       # Stop and remove volumes
```

The Kafka broker is accessible at `localhost:29092` from the host and at `kafka:9092` from within the Docker network. The schema registry is accessible at `localhost:8081`.
