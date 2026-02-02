package io.schemaregistry.abtest.runner;

import io.schemaregistry.abtest.config.AbTestProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class HttpExecutor {

    private final AbTestProperties properties;
    private final RestTemplate restTemplate;

    public HttpExecutor(AbTestProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    public record Response(int status, String body, String contentType) {}

    public record DualResponse(Response confluent, Response mirror) {}

    public DualResponse execute(String method, String endpoint, String body) {
        Response confluent = sendRequest(properties.getConfluentUrl(), method, endpoint, body, null, null);
        Response mirror = sendRequest(properties.getMirrorUrl(), method, endpoint, body,
                properties.getMirrorUsername(), properties.getMirrorPassword());
        return new DualResponse(confluent, mirror);
    }

    private Response sendRequest(String baseUrl, String method, String endpoint, String body,
                                 String username, String password) {
        String url = baseUrl + endpoint;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.schemaregistry.v1+json"));

        if (username != null && password != null && !password.isEmpty()) {
            String auth = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + auth);
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, entity, String.class);
            String contentType = response.getHeaders().getContentType() != null
                    ? response.getHeaders().getContentType().toString() : "";
            return new Response(response.getStatusCode().value(),
                    response.getBody() != null ? response.getBody() : "", contentType);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String contentType = e.getResponseHeaders() != null && e.getResponseHeaders().getContentType() != null
                    ? e.getResponseHeaders().getContentType().toString() : "";
            return new Response(e.getStatusCode().value(), e.getResponseBodyAsString(), contentType);
        } catch (Exception e) {
            return new Response(0, "Connection error: " + e.getMessage(), "");
        }
    }
}
