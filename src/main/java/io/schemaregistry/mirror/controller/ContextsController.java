package io.schemaregistry.mirror.controller;

import io.schemaregistry.mirror.config.WebMvcConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ContextsController {

    @GetMapping(value = "/contexts", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public List<String> getContexts() {
        // Default context only (no multi-tenancy)
        return List.of(".");
    }
}
