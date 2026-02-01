package io.schemaregistry.mirror.controller;

import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.schemaregistry.mirror.config.WebMvcConfig;
import io.schemaregistry.mirror.service.SchemaRegistryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SubjectsController {

    private final SchemaRegistryService service;

    public SubjectsController(SchemaRegistryService service) {
        this.service = service;
    }

    @GetMapping(value = "/subjects", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public List<String> listSubjects(
            @RequestParam(value = "subjectPrefix", required = false) String subjectPrefix,
            @RequestParam(value = "deleted", required = false, defaultValue = "false") boolean deleted) {
        return service.listSubjects(subjectPrefix, deleted);
    }

    @PostMapping(value = "/subjects/{subject}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON}, consumes = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON, WebMvcConfig.OCTET_STREAM})
    public Schema lookupSchema(
            @PathVariable("subject") String subject,
            @RequestBody RegisterSchemaRequest request,
            @RequestParam(value = "normalize", required = false, defaultValue = "false") boolean normalize,
            @RequestParam(value = "deleted", required = false, defaultValue = "false") boolean deleted) {
        return service.lookupSchema(subject, request, normalize, deleted);
    }

    @DeleteMapping(value = "/subjects/{subject}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public List<Integer> deleteSubject(
            @PathVariable("subject") String subject,
            @RequestParam(value = "permanent", required = false, defaultValue = "false") boolean permanent) {
        return service.deleteSubject(subject, permanent);
    }
}
