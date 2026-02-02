package io.schemaregistry.mirror;

import io.schemaregistry.mirror.service.SchemaRegistryService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpMetricsTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private SchemaRegistryService schemaRegistryService;

    @MockitoBean
    private KafkaSchemaStore kafkaSchemaStore;

    @MockitoBean
    private AdminClient adminClient;

    @MockitoBean
    private KafkaProducer<byte[], byte[]> kafkaProducer;

    @MockitoBean
    private KafkaConsumer<byte[], byte[]> kafkaConsumer;

    @Test
    void apiCallProducesHttpServerRequestMetrics() {
        when(schemaRegistryService.listSubjects(any(), eq(false)))
            .thenReturn(List.of("test-subject"));
        when(kafkaSchemaStore.getSubjects(anyBoolean())).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(0);

        // Make an API call to produce metrics
        ResponseEntity<String> apiResponse = restTemplate.getForEntity("/subjects", String.class);
        assertEquals(200, apiResponse.getStatusCode().value());

        // Check that Prometheus output contains http_server_requests metrics
        ResponseEntity<String> promResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertEquals(200, promResponse.getStatusCode().value());
        String body = promResponse.getBody();
        assertTrue(body.contains("http_server_requests_seconds"), "Should contain http_server_requests_seconds");
        assertTrue(body.contains("uri=\"/subjects\""), "Should contain uri=/subjects");
        assertTrue(body.contains("method=\"GET\""), "Should contain method=GET");
        assertTrue(body.contains("status=\"200\""), "Should contain status=200");
    }

    @Test
    void multipleEndpointsProduceDistinctMetrics() {
        when(schemaRegistryService.listSubjects(any(), eq(false)))
            .thenReturn(List.of("test-subject"));
        when(schemaRegistryService.getSchemaTypes())
            .thenReturn(List.of("AVRO", "JSON", "PROTOBUF"));
        when(kafkaSchemaStore.getSubjects(anyBoolean())).thenReturn(Collections.emptyList());
        when(kafkaSchemaStore.getMaxSchemaId()).thenReturn(0);

        // Hit two different endpoints
        restTemplate.getForEntity("/subjects", String.class);
        restTemplate.getForEntity("/schemas/types", String.class);

        // Check Prometheus output contains metrics for both
        ResponseEntity<String> promResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertEquals(200, promResponse.getStatusCode().value());
        String body = promResponse.getBody();
        assertTrue(body.contains("uri=\"/subjects\""), "Should contain uri=/subjects");
        assertTrue(body.contains("uri=\"/schemas/types\""), "Should contain uri=/schemas/types");
    }
}
