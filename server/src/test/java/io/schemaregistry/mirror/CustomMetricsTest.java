package io.schemaregistry.mirror;

import io.micrometer.core.instrument.MeterRegistry;
import io.schemaregistry.mirror.storage.InMemoryStore;
import io.schemaregistry.mirror.storage.KafkaSchemaStore;
import io.schemaregistry.mirror.storage.model.SchemaValue;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@SpringBootTest
class CustomMetricsTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private KafkaSchemaStore kafkaSchemaStore;

    @MockitoBean
    private AdminClient adminClient;

    @MockitoBean
    private KafkaProducer<byte[], byte[]> kafkaProducer;

    @MockitoBean
    private KafkaConsumer<byte[], byte[]> kafkaConsumer;

    @Test
    void subjectsCountGaugeReflectsActiveSubjects() {
        when(kafkaSchemaStore.getSubjects(false)).thenReturn(List.of("subject-a", "subject-b"));
        when(kafkaSchemaStore.getSubjects(true)).thenReturn(List.of("subject-a", "subject-b", "deleted-subject"));
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(5);

        double activeCount = meterRegistry.get("schema.registry.subjects.count").gauge().value();
        assertEquals(2.0, activeCount);
    }

    @Test
    void schemasCountGaugeReflectsMaxId() {
        when(kafkaSchemaStore.getSubjects(false)).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getSubjects(true)).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(42);

        double schemasCount = meterRegistry.get("schema.registry.schemas.count").gauge().value();
        assertEquals(42.0, schemasCount);
    }

    @Test
    void totalSubjectsCountIncludesDeleted() {
        when(kafkaSchemaStore.getSubjects(false)).thenReturn(List.of("active-subject"));
        when(kafkaSchemaStore.getSubjects(true)).thenReturn(List.of("active-subject", "deleted-subject"));
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(3);

        double totalCount = meterRegistry.get("schema.registry.subjects.total.count").gauge().value();
        assertEquals(2.0, totalCount);
    }

    @Test
    void gaugesUpdateDynamically() {
        // Initial state: no subjects
        when(kafkaSchemaStore.getSubjects(false)).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getSubjects(true)).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(0);

        assertEquals(0.0, meterRegistry.get("schema.registry.subjects.count").gauge().value());
        assertEquals(0.0, meterRegistry.get("schema.registry.schemas.count").gauge().value());

        // State changes: subjects added
        when(kafkaSchemaStore.getSubjects(false)).thenReturn(List.of("subject-1", "subject-2", "subject-3"));
        when(kafkaSchemaStore.getSubjects(true)).thenReturn(List.of("subject-1", "subject-2", "subject-3"));
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(10);

        assertEquals(3.0, meterRegistry.get("schema.registry.subjects.count").gauge().value());
        assertEquals(10.0, meterRegistry.get("schema.registry.schemas.count").gauge().value());
        assertEquals(3.0, meterRegistry.get("schema.registry.subjects.total.count").gauge().value());
    }

    @Test
    void allCustomGaugesAreRegistered() {
        when(kafkaSchemaStore.getSubjects(false)).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getSubjects(true)).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(0);

        assertNotNull(meterRegistry.get("schema.registry.subjects.count").gauge());
        assertNotNull(meterRegistry.get("schema.registry.schemas.count").gauge());
        assertNotNull(meterRegistry.get("schema.registry.subjects.total.count").gauge());
    }
}
