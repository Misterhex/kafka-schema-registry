package io.schemaregistry.mirror.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.schemaregistry.mirror.storage.KafkaSchemaStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterBinder schemaRegistryMetrics(KafkaSchemaStore store) {
        return (MeterRegistry registry) -> {
            registry.gauge("schema.registry.subjects.count", store,
                s -> s.getSubjects(false).size());

            registry.gauge("schema.registry.schemas.count", store,
                KafkaSchemaStore::getMaxSchemaId);

            registry.gauge("schema.registry.subjects.total.count", store,
                s -> s.getSubjects(true).size());
        };
    }
}
