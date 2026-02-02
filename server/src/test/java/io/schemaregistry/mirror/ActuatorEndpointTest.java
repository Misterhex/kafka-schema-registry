package io.schemaregistry.mirror;

import io.schemaregistry.mirror.storage.KafkaSchemaStore;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorEndpointTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private KafkaSchemaStore kafkaSchemaStore;

    @MockitoBean
    private AdminClient adminClient;

    @MockitoBean
    private KafkaProducer<byte[], byte[]> kafkaProducer;

    @MockitoBean
    private KafkaConsumer<byte[], byte[]> kafkaConsumer;

    @Test
    void prometheusEndpointReturnsJvmMetrics() {
        when(kafkaSchemaStore.getSubjects(anyBoolean())).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(0);

        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("jvm_memory"));
        assertTrue(response.getBody().contains("process_cpu"));
    }

    @Test
    void healthEndpointIsAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void prometheusEndpointContainsCustomMetrics() {
        when(kafkaSchemaStore.getSubjects(false)).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getSubjects(true)).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(0);

        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertEquals(200, response.getStatusCode().value());
        String body = response.getBody();
        assertTrue(body.contains("schema_registry_subjects_count"), "Should contain schema_registry_subjects_count");
        assertTrue(body.contains("schema_registry_schemas_count"), "Should contain schema_registry_schemas_count");
        assertTrue(body.contains("schema_registry_subjects_total_count"), "Should contain schema_registry_subjects_total_count");
    }

    @Test
    void metricsEndpointIsAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics", String.class);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void prometheusEndpointContainsApplicationTag() {
        when(kafkaSchemaStore.getSubjects(anyBoolean())).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(0);

        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("application=\"schema-registry-mirror\""),
            "Should contain application tag");
    }
}
