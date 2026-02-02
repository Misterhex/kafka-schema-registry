package io.schemaregistry.mirror.controller;

import io.schemaregistry.mirror.config.WebMvcConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping(value = "/", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, Object> root() {
        return Collections.emptyMap();
    }
}
