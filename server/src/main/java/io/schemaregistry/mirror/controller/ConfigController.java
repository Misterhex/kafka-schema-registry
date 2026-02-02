package io.schemaregistry.mirror.controller;

import io.schemaregistry.mirror.config.WebMvcConfig;
import io.schemaregistry.mirror.exception.SchemaRegistryException;
import io.schemaregistry.mirror.schema.CompatibilityLevel;
import io.schemaregistry.mirror.service.SchemaRegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ConfigController {

    private final SchemaRegistryService service;

    public ConfigController(SchemaRegistryService service) {
        this.service = service;
    }

    @GetMapping(value = "/config", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> getGlobalConfig() {
        return service.getGlobalConfig();
    }

    @PutMapping(value = "/config", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON}, consumes = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON, WebMvcConfig.OCTET_STREAM})
    public Map<String, String> setGlobalConfig(@RequestBody Map<String, String> request) {
        String levelStr = request.get("compatibility");
        CompatibilityLevel level = parseCompatibilityLevel(levelStr);
        return service.setGlobalConfig(level);
    }

    @DeleteMapping(value = "/config", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> deleteGlobalConfig() {
        String previousLevel = service.getGlobalConfig().get("compatibilityLevel");
        service.setGlobalConfig(CompatibilityLevel.BACKWARD);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("compatibilityLevel", previousLevel);
        return result;
    }

    @GetMapping(value = "/config/{subject}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> getSubjectConfig(
            @PathVariable("subject") String subject,
            @RequestParam(value = "defaultToGlobal", required = false, defaultValue = "true") boolean defaultToGlobal) {
        return service.getSubjectConfig(subject, defaultToGlobal);
    }

    @PutMapping(value = "/config/{subject}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON}, consumes = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON, WebMvcConfig.OCTET_STREAM})
    public Map<String, String> setSubjectConfig(
            @PathVariable("subject") String subject,
            @RequestBody Map<String, String> request) {
        String levelStr = request.get("compatibility");
        CompatibilityLevel level = parseCompatibilityLevel(levelStr);
        return service.setSubjectConfig(subject, level);
    }

    @DeleteMapping(value = "/config/{subject}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> deleteSubjectConfig(@PathVariable("subject") String subject) {
        return service.deleteSubjectConfig(subject);
    }

    private CompatibilityLevel parseCompatibilityLevel(String levelStr) {
        if (levelStr == null || levelStr.isEmpty()) {
            throw SchemaRegistryException.invalidCompatibilityLevelException(null);
        }
        try {
            return CompatibilityLevel.forName(levelStr);
        } catch (IllegalArgumentException e) {
            throw SchemaRegistryException.invalidCompatibilityLevelException(levelStr);
        }
    }
}
