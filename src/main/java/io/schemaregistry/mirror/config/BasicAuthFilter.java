package io.schemaregistry.mirror.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BasicAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthFilter.class);
    private static final String CONTENT_TYPE = WebMvcConfig.SCHEMA_REGISTRY_V1_JSON;

    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;

    public BasicAuthFilter(SchemaRegistryProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.username = properties.getAuth().getUsername();

        String configuredPassword = properties.getAuth().getPassword();
        if (configuredPassword == null || configuredPassword.isBlank()) {
            this.password = generateRandomPassword();
            log.info("No password configured. Generated password: {}", this.password);
        } else {
            this.password = configuredPassword;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            sendUnauthorized(response);
            return;
        }

        String credentials;
        try {
            byte[] decoded = Base64.getDecoder().decode(authHeader.substring(6));
            credentials = new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            sendUnauthorized(response);
            return;
        }

        int colonIndex = credentials.indexOf(':');
        if (colonIndex < 0) {
            sendUnauthorized(response);
            return;
        }

        String providedUsername = credentials.substring(0, colonIndex);
        String providedPassword = credentials.substring(colonIndex + 1);

        if (!username.equals(providedUsername) || !password.equals(providedPassword)) {
            sendUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(CONTENT_TYPE);
        response.setHeader("WWW-Authenticate", "Basic realm=\"Schema Registry\"");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", 40101);
        body.put("message", "Unauthorized");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    static String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
