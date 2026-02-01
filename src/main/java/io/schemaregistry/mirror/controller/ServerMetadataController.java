package io.schemaregistry.mirror.controller;

import io.schemaregistry.mirror.config.SchemaRegistryProperties;
import io.schemaregistry.mirror.config.WebMvcConfig;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class ServerMetadataController {

    private static final Logger log = LoggerFactory.getLogger(ServerMetadataController.class);

    private final AdminClient adminClient;
    private final SchemaRegistryProperties properties;

    public ServerMetadataController(AdminClient adminClient, SchemaRegistryProperties properties) {
        this.adminClient = adminClient;
        this.properties = properties;
    }

    @GetMapping(value = "/v1/metadata/id", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, Object> getClusterId() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String clusterId = adminClient.describeCluster()
                .clusterId()
                .get(10, TimeUnit.SECONDS);
            result.put("id", clusterId);
            result.put("scope", Map.of());
        } catch (Exception e) {
            log.error("Error getting cluster ID", e);
            result.put("id", null);
            result.put("scope", Map.of());
        }
        return result;
    }

    @GetMapping(value = "/v1/metadata/version", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> getVersion() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("version", "0.1.0-mirror");
        return result;
    }
}
