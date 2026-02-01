# Schema Registry Mirror

A wire-compatible Confluent Schema Registry implementation built with Spring Boot. It stores schemas in a Kafka topic (`_schemas`) and materializes them in memory, providing the same REST API as the Confluent Schema Registry.

## Quick Start

### Using Docker Compose

```bash
docker compose up -d
```

This starts Kafka (KRaft mode, no ZooKeeper) and the schema registry on port 8081.

### Running the JAR Directly

Requires a running Kafka broker.

```bash
./gradlew bootJar
java -jar build/libs/schema-registry-mirror-0.1.0-SNAPSHOT.jar
```

## Configuration

All settings can be overridden via environment variables:

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

```bash
./gradlew build -x test
```

To run tests (requires Docker for Testcontainers):

```bash
./gradlew test
```

## Docker

### Build the image

```bash
docker build -t schema-registry-mirror .
```

### Run with Docker Compose

```bash
docker compose up -d        # start
docker compose logs -f       # view logs
docker compose down -v       # stop and clean up
```

### Run the test suite

```bash
docker compose up -d
./test.sh
docker compose down -v
```

## Architecture

The mirror reads and writes schema records to a compacted Kafka topic (`_schemas` by default). On startup, a `KafkaStoreReaderThread` consumes the topic from the beginning and materializes all records into an `InMemoryStore`. This provides:

- **Durability** — schemas survive restarts via Kafka's log
- **Consistency** — writes go through Kafka, ensuring a total order
- **Low latency** — reads are served from memory

The storage layer uses typed key/value pairs (`SchemaKey`, `ConfigKey`, `ModeKey`, `DeleteSubjectKey`, `ClearSubjectKey`) serialized as JSON. Each key type corresponds to a different class of metadata stored in the topic.
