package com.tlq.rectagent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class OpenAIProviderIntegrationTest {
    @Test
    public void openAiCallsEndpointAndParsesResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String resp = "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"openai reply text\"}}]}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();
        String endpoint = "http://localhost:" + port + "/v1/chat/completions";

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-test");
        cfg.setEnabled(true);
        cfg.setType("openai");
        cfg.setModel("gpt-4");
        cfg.setCostPerToken(0.2);
        cfg.setApiKey("dummy");
        cfg.setEndpoint(endpoint);
        cfg.setMock(false);
        cfg.setMaxRetries(1);
        cfg.setRetryDelayMs(0);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        String result = provider.call("hello");
        assertTrue(result.contains("openai reply text"));

        server.stop(0);
    }
}
