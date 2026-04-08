package io.schemaregistry.mirror.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps a handful of Confluent Schema Registry environment variables that do
 * not have a straightforward 1:1 property translation onto our Spring Boot
 * properties. Runs before {@code @ConfigurationProperties} binding.
 *
 * <p>Currently handles:
 * <ul>
 *   <li>{@code SCHEMA_REGISTRY_LISTENERS} — Confluent's listener URL
 *       (e.g. {@code http://0.0.0.0:8081}). We parse the port out of the
 *       first listener and push it into {@code server.port}, and we set
 *       {@code schema.registry.inter-instance-protocol} from the scheme
 *       unless it has been set explicitly elsewhere.</li>
 * </ul>
 *
 * <p>Registered via {@code META-INF/spring.factories}.
 */
public class ConfluentEnvCompatPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ConfluentEnvCompatPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String listeners = environment.getProperty("SCHEMA_REGISTRY_LISTENERS");
        if (listeners == null || listeners.isBlank()) {
            return;
        }

        // SCHEMA_REGISTRY_LISTENERS may contain multiple comma-separated URLs.
        // We only honour the first one for server.port, matching the common
        // single-listener production deployment.
        String first = listeners.split(",")[0].trim();
        try {
            URI uri = URI.create(first);
            Map<String, Object> overrides = new HashMap<>();
            if (uri.getPort() != -1) {
                overrides.put("server.port", uri.getPort());
                overrides.put("schema.registry.inter-instance-port", uri.getPort());
            }
            if (uri.getScheme() != null) {
                overrides.put("schema.registry.inter-instance-protocol", uri.getScheme());
            }
            if (!overrides.isEmpty()) {
                environment.getPropertySources().addFirst(
                        new MapPropertySource("confluent-listeners-compat", overrides));
                log.info("Mapped SCHEMA_REGISTRY_LISTENERS={} -> {}", listeners, overrides);
            }
        } catch (Exception e) {
            log.warn("Failed to parse SCHEMA_REGISTRY_LISTENERS={}: {}", listeners, e.getMessage());
        }
    }
}
