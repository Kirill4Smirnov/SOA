package ru.soa.marketplace.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.NullNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE");
    private final ObjectMapper objectMapper;

    public RequestLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        response.setHeader("X-Request-Id", requestId);
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("request_id", requestId);
            entry.put("method", request.getMethod());
            entry.put("endpoint", request.getRequestURI());
            entry.put("status_code", response.getStatus());
            entry.put("duration_ms", (System.nanoTime() - started) / 1_000_000.0);
            Object userId = request.getAttribute("user_id");
            entry.put("user_id", userId == null ? NullNode.getInstance() : userId);
            entry.put("timestamp", Instant.now().toString());
            if (MUTATING_METHODS.contains(request.getMethod())) {
                entry.put("request_body", maskedBody(wrapped));
            }
            log.info(objectMapper.writeValueAsString(entry));
        }
    }

    private Object maskedBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length == 0) return null;
        String body = new String(content, StandardCharsets.UTF_8);
        try {
            JsonNode node = objectMapper.readTree(body);
            maskPasswords(node);
            return node;
        } catch (Exception exception) {
            return body.replaceAll("(?i)(\\\"password\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
        }
    }

    private void maskPasswords(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.fieldNames().forEachRemaining(name -> {
                if (name.toLowerCase().contains("password")) objectNode.put(name, "***");
                else maskPasswords(objectNode.get(name));
            });
        } else if (node != null && node.isArray()) {
            node.forEach(this::maskPasswords);
        }
    }
}
