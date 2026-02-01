package io.schemaregistry.mirror.controller;

import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.schemaregistry.client.rest.entities.SubjectVersion;
import io.schemaregistry.mirror.config.WebMvcConfig;
import io.schemaregistry.mirror.service.SchemaRegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SchemasController {

    private final SchemaRegistryService service;

    public SchemasController(SchemaRegistryService service) {
        this.service = service;
    }

    @GetMapping(value = "/schemas/ids/{id}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public SchemaString getSchemaById(
            @PathVariable("id") int id,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "fetchMaxId", required = false, defaultValue = "false") boolean fetchMaxId) {
        return service.getSchemaStringById(id, subject, fetchMaxId);
    }

    @GetMapping(value = "/schemas/ids/{id}/schema", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public String getRawSchemaById(
            @PathVariable("id") int id,
            @RequestParam(value = "subject", required = false) String subject) {
        return service.getRawSchemaById(id, subject);
    }

    @GetMapping(value = "/schemas/ids/{id}/subjects", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public List<String> getSubjectsForSchemaId(
            @PathVariable("id") int id,
            @RequestParam(value = "deleted", required = false, defaultValue = "false") boolean deleted) {
        return service.getSubjectsForSchemaId(id, deleted);
    }

    @GetMapping(value = "/schemas/ids/{id}/versions", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public List<SubjectVersion> getVersionsForSchemaId(
            @PathVariable("id") int id,
            @RequestParam(value = "deleted", required = false, defaultValue = "false") boolean deleted) {
        return service.getVersionsForSchemaId(id, deleted);
    }

    @GetMapping(value = "/schemas/types", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public List<String> getSchemaTypes() {
        return service.getSchemaTypes();
    }
}
