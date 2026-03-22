package com.tlq.rectagent.model;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AnthropicProvider implements ModelProvider {
    private final ModelProviderConfig config;
    public AnthropicProvider(ModelProviderConfig config) { this.config = config; }
    @Override
    public String getName() { return config.getName() != null ? config.getName() : "anthropic"; }
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
            return "[Anthropic:" + getName() + "] mock response to: " + input;
        }
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Anthropic API key not configured");
        }
        String endpoint = config.getEndpoint();
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = "https://api.anthropic.com/v1/messages";
        }
        String model = config.getModel();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.put("max_tokens", 1024);
        payload.put("temperature", 0.7);
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "user");
        msg.put("content", input);
        payload.putArray("messages").add(msg);
        String body = mapper.writeValueAsString(payload);

        int maxRetries = Math.max(0, config.getMaxRetries());
        long backoff = Math.max(0, config.getRetryDelayMs());
        HttpClient client = HttpClient.newHttpClient();
        String respBody = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<String> resp;
            try {
                resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                if (attempt == maxRetries) throw e;
                if (backoff > 0) Thread.sleep(backoff);
                continue;
            }
            int code = resp.statusCode();
            respBody = resp.body();
            if (code >= 200 && code < 300) {
                try {
                    JsonNode root = mapper.readTree(respBody);
                    JsonNode content = root.path("content");
                    if (content.isArray() && content.size() > 0) {
                        JsonNode text = content.get(0).path("text");
                        if (!text.isMissingNode()) return text.asText();
                    }
                } catch (Exception ignored) {}
                return respBody;
            } else {
                if (attempt == maxRetries) {
                    throw new RuntimeException("Anthropic endpoint error: " + code + "\n" + respBody);
                }
                if (backoff > 0) Thread.sleep(backoff);
            }
        }
        if (respBody != null) return respBody;
        throw new RuntimeException("Anthropic call failed");
    }
}
