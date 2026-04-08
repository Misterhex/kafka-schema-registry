package io.schemaregistry.mirror.leader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.schemaregistry.mirror.config.SchemaRegistryProperties;
import io.schemaregistry.mirror.config.WebMvcConfig;
import io.schemaregistry.mirror.exception.SchemaRegistryException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single-primary HTTP forwarding filter.
 *
 * <p>For any mutating request (POST, PUT, DELETE) on a Schema Registry REST
 * endpoint, this filter checks whether the current node is the elected
 * leader. If not, it proxies the request verbatim to the leader's advertised
 * URL and streams the response back.
 *
 * <p><b>Loop prevention</b> — when forwarding, the filter sets the
 * {@code X-Forward: true} request header (the same header Confluent
 * Schema Registry's own client library uses, see
 * {@code RestService.X_FORWARD_HEADER}). On receiving a request with that
 * header set the filter unconditionally processes the request locally
 * regardless of leadership state. The legacy {@code ?forward=false} query
 * parameter is also honoured for backwards-compatibility with older
 * tooling.
 *
 * <p>Behavioural matches for Confluent Schema Registry:
 * <ul>
 *   <li>Loop-stop signal is the {@code X-Forward} header.</li>
 *   <li>Error code {@code 50003} when the forwarded HTTP call fails.</li>
 *   <li>Error code {@code 50004} when no leader is currently known.</li>
 * </ul>
 *
 * <p>Registered explicitly (not as a {@code @Component}) via
 * {@link io.schemaregistry.mirror.config.WebMvcConfig} so that its ordering
 * relative to {@link io.schemaregistry.mirror.config.BasicAuthFilter} is
 * deterministic.
 */
public class LeaderForwardingFilter extends OncePerRequestFilter {

    /**
     * Header used by Confluent SR's own client library and inter-node forwarding
     * to signal "this request was forwarded by a peer, do not bounce it again."
     */
    public static final String X_FORWARD_HEADER = "X-Forward";

    private static final Logger log = LoggerFactory.getLogger(LeaderForwardingFilter.class);

    /** HTTP methods that mutate state and therefore must be served by the leader. */
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE");

    /** Headers that must NOT be copied from the incoming request to the forwarded one. */
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length");

    private final LeaderState leaderState;
    private final SchemaRegistryProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public LeaderForwardingFilter(LeaderState leaderState,
                                  SchemaRegistryProperties properties,
                                  ObjectMapper objectMapper) {
        this.leaderState = leaderState;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getForwardingRequestTimeoutMs()))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Don't forward health/metrics endpoints; they are node-local.
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!shouldForward(request)) {
            chain.doFilter(request, response);
            return;
        }

        LeaderIdentity leader = leaderState.getCurrentLeader();
        if (leader == null) {
            writeError(response, 500, SchemaRegistryException.UNKNOWN_LEADER_ERROR_CODE,
                    "Unknown leader: no leader has been elected yet");
            return;
        }

        if (leaderState.isLeader()) {
            // Race: we became leader between shouldForward() and here.
            chain.doFilter(request, response);
            return;
        }

        URI target = buildTargetUri(leader, request);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofMillis(properties.getForwardingRequestTimeoutMs()));

            byte[] body = request.getInputStream().readAllBytes();
            builder.method(request.getMethod(), HttpRequest.BodyPublishers.ofByteArray(body));

            copyRequestHeaders(request, builder);
            // Confluent SR convention — tells the leader not to forward
            // again. The .header() call after copyRequestHeaders ensures
            // we win even if the incoming request had its own (false) value.
            builder.header(X_FORWARD_HEADER, "true");

            log.debug("Forwarding {} {} to leader {}", request.getMethod(), request.getRequestURI(), target);
            HttpResponse<byte[]> forwardedResponse = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

            writeForwardedResponse(forwardedResponse, response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeError(response, 500, SchemaRegistryException.REQUEST_FORWARDING_FAILED_ERROR_CODE,
                    "Request forwarding interrupted");
        } catch (IOException | RuntimeException e) {
            log.warn("Forwarding to leader {} failed: {}", target, e.getMessage());
            writeError(response, 500, SchemaRegistryException.REQUEST_FORWARDING_FAILED_ERROR_CODE,
                    "Error forwarding request to leader " + leader.url() + ": " + e.getMessage());
        }
    }

    private boolean shouldForward(HttpServletRequest request) {
        if (!MUTATING_METHODS.contains(request.getMethod())) {
            return false;
        }
        if (isReadOnlyPost(request)) {
            return false;
        }
        // Confluent SR convention: X-Forward: true means "I'm a peer, don't
        // bounce this back." We honour it whether we're leader or follower
        // and process the request locally.
        String xForward = request.getHeader(X_FORWARD_HEADER);
        if (xForward != null && "true".equalsIgnoreCase(xForward)) {
            return false;
        }
        // Legacy fallback: ?forward=false query parameter (used by some
        // older tooling and our test scripts).
        String forwardParam = request.getParameter("forward");
        if (forwardParam != null && "false".equalsIgnoreCase(forwardParam)) {
            return false;
        }
        // No sense forwarding if we are the leader.
        return !leaderState.isLeader();
    }

    /**
     * Two REST endpoints use POST as a verb but are pure reads against the
     * in-memory store, so they don't need to be funnelled through the
     * leader: compatibility checks (POST {@code /compatibility/...}) and
     * schema lookup-by-content (POST {@code /subjects/{subject}}). Matching
     * Confluent SR's behaviour.
     */
    private boolean isReadOnlyPost(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/compatibility/")) {
            return true;
        }
        // POST /subjects/{subject} (with no further path) is "look up
        // schema by content"; POST /subjects/{subject}/versions is the
        // mutating registration call.
        if (uri.startsWith("/subjects/") && !uri.contains("/versions")) {
            return true;
        }
        return false;
    }

    private URI buildTargetUri(LeaderIdentity leader, HttpServletRequest request) {
        StringBuilder uri = new StringBuilder();
        uri.append(leader.url());
        uri.append(request.getRequestURI());

        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            uri.append("?forward=false");
        } else if (query.contains("forward=")) {
            uri.append('?').append(query);
        } else {
            uri.append('?').append(query).append("&forward=false");
        }
        return URI.create(uri.toString());
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String lower = name.toLowerCase();
            if (HOP_BY_HOP_HEADERS.contains(lower)) {
                continue;
            }
            // We set X-Forward ourselves on the outgoing request, so skip
            // any incoming value to avoid emitting two copies of the header.
            if (X_FORWARD_HEADER.equalsIgnoreCase(name)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                String value = values.nextElement();
                try {
                    builder.header(name, value);
                } catch (IllegalArgumentException ex) {
                    // JDK HttpClient restricts some header names; skip them.
                }
            }
        }
    }

    private void writeForwardedResponse(HttpResponse<byte[]> forwarded, HttpServletResponse response) throws IOException {
        response.setStatus(forwarded.statusCode());
        Map<String, List<String>> headers = forwarded.headers().map();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                continue;
            }
            for (String value : entry.getValue()) {
                response.addHeader(name, value);
            }
        }
        byte[] body = forwarded.body();
        if (body != null && body.length > 0) {
            try (OutputStream out = response.getOutputStream()) {
                out.write(body);
            }
        }
    }

    private void writeError(HttpServletResponse response, int status, int errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(WebMvcConfig.SCHEMA_REGISTRY_V1_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", errorCode);
        body.put("message", message);
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        response.getOutputStream().write(bytes);
    }
}
