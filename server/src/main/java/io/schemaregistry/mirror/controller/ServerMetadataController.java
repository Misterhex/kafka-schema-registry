package io.schemaregistry.mirror.controller;

import io.schemaregistry.mirror.config.WebMvcConfig;
import io.schemaregistry.mirror.leader.LeaderIdentity;
import io.schemaregistry.mirror.leader.LeaderState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ServerMetadataController {

    private final LeaderState leaderState;

    public ServerMetadataController(LeaderState leaderState) {
        this.leaderState = leaderState;
    }

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

    /**
     * Diagnostic endpoint exposing the current leader as observed by this
     * node's view of the {@code _schemas} log. Returns the leader's host,
     * port, scheme, advertised URL, and whether this node is itself the
     * leader. {@code null} fields when no leader has been elected yet.
     */
    @GetMapping(value = "/v1/metadata/leader", produces = {WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, WebMvcConfig.SCHEMA_REGISTRY_DEFAULT_JSON, WebMvcConfig.JSON})
    public Map<String, Object> getLeader() {
        Map<String, Object> result = new LinkedHashMap<>();
        LeaderIdentity leader = leaderState.getCurrentLeader();
        LeaderIdentity me = leaderState.getMyIdentity();
        if (leader != null) {
            result.put("host", leader.getHost());
            result.put("port", leader.getPort());
            result.put("scheme", leader.getScheme());
            result.put("url", leader.url());
            result.put("version", leader.getVersion());
        } else {
            result.put("host", null);
            result.put("port", null);
            result.put("scheme", null);
            result.put("url", null);
        }
        result.put("isLeader", leaderState.isLeader());
        if (me != null) {
            result.put("self", me.url());
        }
        return result;
    }
}
