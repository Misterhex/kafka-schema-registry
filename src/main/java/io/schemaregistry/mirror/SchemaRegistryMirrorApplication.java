package io.schemaregistry.mirror;

import io.schemaregistry.mirror.config.SchemaRegistryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SchemaRegistryProperties.class)
public class SchemaRegistryMirrorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchemaRegistryMirrorApplication.class, args);
    }
}
