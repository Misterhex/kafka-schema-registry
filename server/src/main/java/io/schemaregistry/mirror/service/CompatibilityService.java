package io.schemaregistry.mirror.service;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.ParsedSchemaHolder;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.schemaregistry.mirror.exception.SchemaRegistryException;
import io.schemaregistry.mirror.schema.CompatibilityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompatibilityService {

    private static final Logger log = LoggerFactory.getLogger(CompatibilityService.class);

    private final Map<String, SchemaProvider> providers;

    public CompatibilityService() {
        providers = new LinkedHashMap<>();
        SchemaProvider avro = new AvroSchemaProvider();
        SchemaProvider json = new JsonSchemaProvider();
        SchemaProvider protobuf = new ProtobufSchemaProvider();
        providers.put("AVRO", avro);
        providers.put("JSON", json);
        providers.put("PROTOBUF", protobuf);
    }

    public ParsedSchema parseSchema(String schemaType, String schema,
                                    List<SchemaReference> references, boolean normalize) {
        String type = schemaType != null ? schemaType : "AVRO";
        SchemaProvider provider = providers.get(type);
        if (provider == null) {
            throw SchemaRegistryException.invalidSchemaException("Invalid schema type: " + type);
        }

        try {
            Optional<ParsedSchema> parsedOpt = provider.parseSchema(schema,
                references != null ? references : Collections.emptyList(),
                normalize);
            if (parsedOpt.isEmpty()) {
                throw SchemaRegistryException.invalidSchemaException("Invalid " + type + " schema");
            }
            return parsedOpt.get();
        } catch (SchemaRegistryException e) {
            throw e;
        } catch (Exception e) {
            throw SchemaRegistryException.invalidSchemaException(
                "Error parsing schema: " + e.getMessage());
        }
    }

    public List<String> testCompatibility(CompatibilityLevel level,
                                          ParsedSchema newSchema,
                                          List<ParsedSchema> previousSchemas) {
        if (level == null || level == CompatibilityLevel.NONE) {
            return Collections.emptyList();
        }

        // Convert our CompatibilityLevel to Confluent's CompatibilityLevel
        io.confluent.kafka.schemaregistry.CompatibilityLevel confluentLevel =
            io.confluent.kafka.schemaregistry.CompatibilityLevel.forName(level.getName());

        if (confluentLevel == null || confluentLevel == io.confluent.kafka.schemaregistry.CompatibilityLevel.NONE) {
            return Collections.emptyList();
        }

        // Wrap previous schemas as ParsedSchemaHolder
        List<ParsedSchemaHolder> holders = previousSchemas.stream()
            .map(ps -> (ParsedSchemaHolder) new SimpleParsedSchemaHolder(ps))
            .collect(Collectors.toList());

        return newSchema.isCompatible(confluentLevel, holders);
    }

    public List<String> getSupportedTypes() {
        return List.of("AVRO", "JSON", "PROTOBUF");
    }

    private static class SimpleParsedSchemaHolder implements ParsedSchemaHolder {
        private ParsedSchema schema;

        SimpleParsedSchemaHolder(ParsedSchema schema) {
            this.schema = schema;
        }

        @Override
        public ParsedSchema schema() {
            return schema;
        }

        @Override
        public void clear() {
            // No-op for in-memory holder
        }
    }
}
