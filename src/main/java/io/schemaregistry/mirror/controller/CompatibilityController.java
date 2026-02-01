package io.schemaregistry.mirror.controller;

import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.schemaregistry.mirror.config.WebMvcConfig;
import io.schemaregistry.mirror.service.SchemaRegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class CompatibilityController {

    private final SchemaRegistryService service;

    public CompatibilityController(SchemaRegistryService service) {
        this.service = service;
    }

    @PostMapping(value = "/compatibility/subjects/{subject}/versions/{version}",
                 produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON}, consumes = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON, WebMvcConfig.OCTET_STREAM})
    public Map<String, Object> testCompatibility(
            @PathVariable("subject") String subject,
            @PathVariable("version") String version,
            @RequestBody RegisterSchemaRequest request,
            @RequestParam(value = "verbose", required = false, defaultValue = "false") boolean verbose) {
        List<String> incompatibilities = service.testCompatibility(subject, version, request, verbose);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("is_compatible", incompatibilities.isEmpty());
        if (verbose && !incompatibilities.isEmpty()) {
            result.put("messages", incompatibilities);
        }
        return result;
    }

    @PostMapping(value = "/compatibility/subjects/{subject}/versions",
                 produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON}, consumes = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON, WebMvcConfig.OCTET_STREAM})
    public Map<String, Object> testCompatibilityLatest(
            @PathVariable("subject") String subject,
            @RequestBody RegisterSchemaRequest request,
            @RequestParam(value = "verbose", required = false, defaultValue = "false") boolean verbose) {
        return testCompatibility(subject, "latest", request, verbose);
    }
}
