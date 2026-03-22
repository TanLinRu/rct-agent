package com.tlq.rectagent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class AnthropicProviderIntegrationTest {
    @Test
    public void returnsMockResponseWhenMockEnabled() throws Exception {
        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("anthropic-mock");
        cfg.setEnabled(true);
        cfg.setType("anthropic");
        cfg.setModel("claude-3-haiku");
        cfg.setMock(true);

        AnthropicProvider provider = new AnthropicProvider(cfg);
        String result = provider.call("hello");
        assertTrue(result.contains("Anthropic:anthropic-mock"));
        assertTrue(result.contains("mock response"));
    }

    @Test
    public void parsesAnthropicResponseFormat() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String expectedText = "parsed-claude-response";
        String mockResp = "{\"type\":\"message\",\"id\":\"msg_01\",\"model\":\"claude-3-haiku\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + expectedText + "\"}]}";
        server.createContext("/messages", exchange -> {
            exchange.getRequestBody();
            exchange.sendResponseHeaders(200, mockResp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(mockResp.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("anthropic-test");
        cfg.setEnabled(true);
        cfg.setType("anthropic");
        cfg.setModel("claude-3-haiku");
        cfg.setEndpoint("http://localhost:" + port + "/messages");
        cfg.setApiKey("test-key");
        cfg.setMaxRetries(0);
        cfg.setMock(false);

        AnthropicProvider provider = new AnthropicProvider(cfg);
        String result = provider.call("hello");
        assertEquals(expectedText, result);

        server.stop(0);
    }

    @Test(expected = IllegalStateException.class)
    public void throwsWhenApiKeyMissing() throws Exception {
        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("anthropic-nokey");
        cfg.setEnabled(true);
        cfg.setType("anthropic");
        cfg.setModel("claude-3-haiku");
        cfg.setApiKey("");
        cfg.setMock(false);

        AnthropicProvider provider = new AnthropicProvider(cfg);
        provider.call("hello");
    }

    @Test
    public void retriesOnFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String mockResp = "{\"type\":\"message\",\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}";
        final int[] attempt = {0};
        server.createContext("/messages", exchange -> {
            attempt[0]++;
            if (attempt[0] < 2) {
                exchange.sendResponseHeaders(500, 0);
                try (OutputStream os = exchange.getResponseBody()) {}
            } else {
                exchange.sendResponseHeaders(200, mockResp.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(mockResp.getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("anthropic-retry");
        cfg.setEnabled(true);
        cfg.setType("anthropic");
        cfg.setModel("claude-3-haiku");
        cfg.setEndpoint("http://localhost:" + port + "/messages");
        cfg.setApiKey("test-key");
        cfg.setMaxRetries(2);
        cfg.setRetryDelayMs(10);
        cfg.setMock(false);

        AnthropicProvider provider = new AnthropicProvider(cfg);
        String result = provider.call("hello");
        assertEquals("ok", result);
        assertEquals(2, attempt[0]);

        server.stop(0);
    }
}
