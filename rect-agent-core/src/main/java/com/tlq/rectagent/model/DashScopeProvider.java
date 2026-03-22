package com.tlq.rectagent.model;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

public class DashScopeProvider implements ModelProvider {
    private final ModelProviderConfig config;
    public DashScopeProvider(ModelProviderConfig config) { this.config = config; }

    @Override
    public String getName() { return config.getName() != null ? config.getName() : "dashscope"; }
    @Override
    public boolean isEnabled() { return config.isEnabled(); }
    @Override
    public double getCostPerToken() { return config.getCostPerToken(); }
    @Override
    public int getPriority() { return config.getPriority(); }
    @Override
    public String getCapability() { return config.getCapability(); }
    @Override
    public String call(String input) throws Exception {
        if (config.isMock()) {
            return "[DashScope:" + getName() + "] mock response to: " + input;
        }
        ObjectMapper mapper = new ObjectMapper();
        int maxRetries = Math.max(0, config.getMaxRetries());
        long backoff = Math.max(0, config.getRetryDelayMs());
        HttpClient client = HttpClient.newHttpClient();

        java.util.List<String> endpoints = config.getEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            String endpoint = config.getEndpoint();
            if (endpoint == null || endpoint.isEmpty()) {
                throw new IllegalStateException("DashScope endpoint not configured");
            }
            endpoints = java.util.Collections.singletonList(endpoint);
        }

        String body = mapper.writeValueAsString(newPayload(config.getModel(), input));
        Exception lastEx = null;
        for (String endpoint : endpoints) {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp;
                try {
                    resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    lastEx = e;
                    if (attempt == maxRetries) break;
                    if (backoff > 0) Thread.sleep(backoff);
                    continue;
                }
                int code = resp.statusCode();
                String respBody = resp.body();
                if (code >= 200 && code < 300) {
                    try {
                        JsonNode root = mapper.readTree(respBody);
                        if (root.has("text")) return root.get("text").asText();
                        if (root.has("content")) return root.get("content").asText();
                    } catch (Exception ignored) {}
                    return respBody;
                } else {
                    if (attempt == maxRetries) {
                        lastEx = new RuntimeException("DashScope endpoint returned status " + code + ": " + respBody);
                        break;
                    }
                    if (backoff > 0) Thread.sleep(backoff);
                }
            }
        }
        if (lastEx != null) throw lastEx;
        throw new RuntimeException("DashScope call failed: all endpoints failed");
    }

    private ObjectNode newPayload(String model, String input) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.put("input", input);
        return payload;
    }
}
