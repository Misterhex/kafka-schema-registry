package io.schemaregistry.mirror.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    public static final String SCHEMA_REGISTRY_V1_JSON = "application/vnd.schemaregistry.v1+json";
    public static final String SCHEMA_REGISTRY_DEFAULT_JSON = "application/vnd.schemaregistry+json";

    public static final MediaType SCHEMA_REGISTRY_V1_JSON_TYPE = MediaType.parseMediaType(SCHEMA_REGISTRY_V1_JSON);
    public static final MediaType SCHEMA_REGISTRY_DEFAULT_JSON_TYPE = MediaType.parseMediaType(SCHEMA_REGISTRY_DEFAULT_JSON);

    public static final String JSON = "application/json";
    public static final String OCTET_STREAM = "application/octet-stream";

    private final ObjectMapper objectMapper;

    public WebMvcConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
            .defaultContentType(SCHEMA_REGISTRY_V1_JSON_TYPE, SCHEMA_REGISTRY_DEFAULT_JSON_TYPE, MediaType.APPLICATION_JSON)
            .favorParameter(false);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Remove existing Jackson converters and add ours with custom media types
        converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converter.setSupportedMediaTypes(List.of(
            SCHEMA_REGISTRY_V1_JSON_TYPE,
            SCHEMA_REGISTRY_DEFAULT_JSON_TYPE,
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_OCTET_STREAM
        ));
        converters.add(0, converter);
    }
}
