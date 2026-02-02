package io.schemaregistry.mirror.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BasicAuthFilterTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "secret";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private BasicAuthFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        SchemaRegistryProperties properties = new SchemaRegistryProperties();
        properties.getAuth().setUsername(USERNAME);
        properties.getAuth().setPassword(PASSWORD);

        filter = new BasicAuthFilter(properties, OBJECT_MAPPER);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void validCredentialsPassThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", basicAuth(USERNAME, PASSWORD));
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingAuthorizationHeaderReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertUnauthorizedBody(response);
    }

    @Test
    void nonBasicSchemeReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer some-token");
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertUnauthorizedBody(response);
    }

    @Test
    void invalidBase64Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Basic !!!not-base64!!!");
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertUnauthorizedBody(response);
    }

    @Test
    void malformedCredentialsNoColonReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String encoded = Base64.getEncoder().encodeToString("nocolon".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Basic " + encoded);
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertUnauthorizedBody(response);
    }

    @Test
    void wrongUsernameReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", basicAuth("wronguser", PASSWORD));
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertUnauthorizedBody(response);
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", basicAuth(USERNAME, "wrongpass"));
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
        assertUnauthorizedBody(response);
    }

    @Test
    void actuatorPathBypassesAuth() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");

        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void apiPathRequiresAuth() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/subjects");

        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    @SuppressWarnings("unchecked")
    void responseFormatVerification() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        Map<String, Object> body = OBJECT_MAPPER.readValue(response.getContentAsString(), Map.class);
        assertEquals(2, body.size());
        assertEquals(40101, body.get("error_code"));
        assertEquals("Unauthorized", body.get("message"));
        assertEquals(WebMvcConfig.SCHEMA_REGISTRY_V1_JSON, response.getContentType());
    }

    @Test
    void wwwAuthenticateHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setRequestURI("/subjects");

        filter.doFilter(request, response, filterChain);

        assertEquals("Basic realm=\"Schema Registry\"", response.getHeader("WWW-Authenticate"));
    }

    @Test
    void generatedPasswordIsNonEmptyUniqueAndUrlSafe() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String pwd = BasicAuthFilter.generateRandomPassword();
            assertNotNull(pwd);
            assertTrue(pwd.length() >= 32, "Generated password should be at least 32 chars, was: " + pwd.length());
            assertTrue(pwd.matches("[A-Za-z0-9_-]+"), "Password should be URL-safe");
            passwords.add(pwd);
        }
        assertEquals(10, passwords.size(), "All generated passwords should be unique");
    }

    @Test
    void autoGenerationWhenNotConfigured() {
        SchemaRegistryProperties properties = new SchemaRegistryProperties();
        properties.getAuth().setPassword("");

        BasicAuthFilter autoFilter = new BasicAuthFilter(properties, OBJECT_MAPPER);
        assertNotNull(autoFilter);
    }

    private static String basicAuth(String user, String pass) {
        String credentials = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private void assertUnauthorizedBody(MockHttpServletResponse response) throws Exception {
        Map<String, Object> body = OBJECT_MAPPER.readValue(response.getContentAsString(), Map.class);
        assertEquals(40101, body.get("error_code"));
        assertEquals("Unauthorized", body.get("message"));
    }
}
