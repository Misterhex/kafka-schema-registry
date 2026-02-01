package io.schemaregistry.mirror.controller;

import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaResponse;
import io.schemaregistry.mirror.config.WebMvcConfig;
import io.schemaregistry.mirror.service.SchemaRegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SubjectVersionsController {

    private final SchemaRegistryService service;

    public SubjectVersionsController(SchemaRegistryService service) {
        this.service = service;
    }

    @GetMapping(value = "/subjects/{subject}/versions", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public List<Integer> listVersions(
            @PathVariable("subject") String subject,
            @RequestParam(value = "deleted", required = false, defaultValue = "false") boolean deleted) {
        return service.listVersions(subject, deleted);
    }

    @GetMapping(value = "/subjects/{subject}/versions/{version}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Schema getSchemaByVersion(
            @PathVariable("subject") String subject,
            @PathVariable("version") String version,
            @RequestParam(value = "deleted", required = false, defaultValue = "false") boolean deleted) {
        return service.getSchemaByVersion(subject, version, deleted);
    }

    @GetMapping(value = "/subjects/{subject}/versions/{version}/schema", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public String getRawSchemaByVersion(
            @PathVariable("subject") String subject,
            @PathVariable("version") String version) {
        return service.getRawSchemaByVersion(subject, version);
    }

    @GetMapping(value = "/subjects/{subject}/versions/{version}/referencedby", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public List<Integer> getReferencedBy(
            @PathVariable("subject") String subject,
            @PathVariable("version") String version) {
        return service.getReferencedBy(subject, version);
    }

    @PostMapping(value = "/subjects/{subject}/versions", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON}, consumes = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON, WebMvcConfig.OCTET_STREAM})
    public RegisterSchemaResponse registerSchema(
            @PathVariable("subject") String subject,
            @RequestBody RegisterSchemaRequest request,
            @RequestParam(value = "normalize", required = false, defaultValue = "false") boolean normalize) {
        int id = service.registerSchema(subject, request, normalize);
        RegisterSchemaResponse response = new RegisterSchemaResponse();
        response.setId(id);
        return response;
    }

    @DeleteMapping(value = "/subjects/{subject}/versions/{version}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Integer deleteSchemaVersion(
            @PathVariable("subject") String subject,
            @PathVariable("version") String version,
            @RequestParam(value = "permanent", required = false, defaultValue = "false") boolean permanent) {
        return service.deleteSchemaVersion(subject, version, permanent);
    }
}
