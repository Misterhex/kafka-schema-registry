package io.schemaregistry.mirror.controller;

import io.schemaregistry.mirror.config.WebMvcConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ServerMetadataController {

    @GetMapping(value = "/v1/metadata/id", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, Object> getClusterId() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", Map.of());
        return result;
    }

    @GetMapping(value = "/v1/metadata/version", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> getVersion() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("version", "0.1.0-mirror");
        result.put("commitId", "unknown");
        return result;
    }
}
