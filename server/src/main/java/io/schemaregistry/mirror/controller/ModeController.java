package io.schemaregistry.mirror.controller;

import io.schemaregistry.mirror.config.WebMvcConfig;
import io.schemaregistry.mirror.service.SchemaRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ModeController {

    private final SchemaRegistryService service;

    public ModeController(SchemaRegistryService service) {
        this.service = service;
    }

    @GetMapping(value = "/mode", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> getGlobalMode() {
        return service.getGlobalMode();
    }

    @PutMapping(value = "/mode", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON}, consumes = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON, WebMvcConfig.OCTET_STREAM})
    public Map<String, String> setGlobalMode(
            @RequestBody Map<String, String> request,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        String mode = request.get("mode");
        return service.setGlobalMode(mode, force);
    }

    @DeleteMapping(value = "/mode", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public ResponseEntity<Map<String, Object>> deleteGlobalMode() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", 405);
        body.put("message", "HTTP 405 Method Not Allowed");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @GetMapping(value = "/mode/{subject}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> getSubjectMode(
            @PathVariable("subject") String subject,
            @RequestParam(value = "defaultToGlobal", required = false, defaultValue = "false") boolean defaultToGlobal) {
        return service.getSubjectMode(subject, defaultToGlobal);
    }

    @PutMapping(value = "/mode/{subject}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON}, consumes = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON, WebMvcConfig.OCTET_STREAM})
    public Map<String, String> setSubjectMode(
            @PathVariable("subject") String subject,
            @RequestBody Map<String, String> request,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        String mode = request.get("mode");
        return service.setSubjectMode(subject, mode, force);
    }

    @DeleteMapping(value = "/mode/{subject}", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, String> deleteSubjectMode(@PathVariable("subject") String subject) {
        return service.deleteSubjectMode(subject);
    }
}
