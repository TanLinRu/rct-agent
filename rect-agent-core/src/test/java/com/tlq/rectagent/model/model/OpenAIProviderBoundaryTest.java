package com.tlq.rectagent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class OpenAIProviderBoundaryTest {
    @Test
    public void throwsOn500Error() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            try (OutputStream os = exchange.getResponseBody()) {}
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-500"); cfg.setEnabled(true); cfg.setType("openai");
        cfg.setModel("gpt-4"); cfg.setEndpoint("http://localhost:" + port + "/v1/chat/completions");
        cfg.setApiKey("test"); cfg.setMaxRetries(0); cfg.setMock(false);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        try {
            provider.call("hello");
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("OpenAI endpoint error: 500"));
        }
        server.stop(0);
    }

    @Test
    public void retriesOn500ThenSucceeds() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        final int[] count = {0};
        server.createContext("/v1/chat/completions", exchange -> {
            count[0]++;
            if (count[0] == 1) {
                exchange.sendResponseHeaders(500, 0);
                try (OutputStream os = exchange.getResponseBody()) {}
            } else {
                String resp = "{\"choices\":[{\"message\":{\"content\":\"success-after-retry\"}}]}";
                exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(resp.getBytes(StandardCharsets.UTF_8)); }
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-retry"); cfg.setEnabled(true); cfg.setType("openai");
        cfg.setModel("gpt-4"); cfg.setEndpoint("http://localhost:" + port + "/v1/chat/completions");
        cfg.setApiKey("test"); cfg.setMaxRetries(2); cfg.setRetryDelayMs(10); cfg.setMock(false);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        String result = provider.call("hello");
        assertEquals("success-after-retry", result);
        assertEquals(2, count[0]);
        server.stop(0);
    }

    @Test
    public void throwsOn400BadRequest() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = "{\"error\":{\"message\":\"Invalid request\",\"type\":\"invalid_request_error\"}}";
            exchange.getRequestBody();
            exchange.sendResponseHeaders(400, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-400"); cfg.setEnabled(true); cfg.setType("openai");
        cfg.setModel("gpt-4"); cfg.setEndpoint("http://localhost:" + port + "/v1/chat/completions");
        cfg.setApiKey("test"); cfg.setMaxRetries(0); cfg.setMock(false);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        try {
            provider.call("hello");
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("OpenAI endpoint error: 400"));
        }
        server.stop(0);
    }

    @Test
    public void throwsOn401Unauthorized() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = "{\"error\":{\"message\":\"Invalid API key\",\"code\":\"invalid_api_key\"}}";
            exchange.getRequestBody();
            exchange.sendResponseHeaders(401, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-401"); cfg.setEnabled(true); cfg.setType("openai");
        cfg.setModel("gpt-4"); cfg.setEndpoint("http://localhost:" + port + "/v1/chat/completions");
        cfg.setApiKey("bad-key"); cfg.setMaxRetries(0); cfg.setMock(false);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        try {
            provider.call("hello");
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("OpenAI endpoint error: 401"));
        }
        server.stop(0);
    }

    @Test
    public void returnsRawResponseWhenParsingFails() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String resp = "not valid json at all";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp.getBytes(StandardCharsets.UTF_8)); }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-raw"); cfg.setEnabled(true); cfg.setType("openai");
        cfg.setModel("gpt-4"); cfg.setEndpoint("http://localhost:" + port + "/v1/chat/completions");
        cfg.setApiKey("test"); cfg.setMaxRetries(0); cfg.setMock(false);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        String result = provider.call("hello");
        assertEquals("not valid json at all", result);
        server.stop(0);
    }

    @Test
    public void throwsWhenApiKeyNotConfigured() throws Exception {
        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-nokey"); cfg.setEnabled(true); cfg.setType("openai");
        cfg.setModel("gpt-4"); cfg.setApiKey(""); cfg.setMock(false);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        try {
            provider.call("hello");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("API key not configured"));
        }
    }

    @Test
    public void throwsOn429RateLimit() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = "{\"error\":{\"message\":\"Rate limit exceeded\",\"type\":\"rate_limit_error\"}}";
            exchange.getRequestBody();
            exchange.sendResponseHeaders(429, body.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-429"); cfg.setEnabled(true); cfg.setType("openai");
        cfg.setModel("gpt-4"); cfg.setEndpoint("http://localhost:" + port + "/v1/chat/completions");
        cfg.setApiKey("test"); cfg.setMaxRetries(0); cfg.setMock(false);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        try {
            provider.call("hello");
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("OpenAI endpoint error: 429"));
        }
        server.stop(0);
    }

    @Test
    public void returnsMockResponseWhenMockEnabled() throws Exception {
        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("openai-mock"); cfg.setEnabled(true); cfg.setType("openai");
        cfg.setModel("gpt-4"); cfg.setMock(true);

        OpenAIProvider provider = new OpenAIProvider(cfg);
        String result = provider.call("test input");
        assertTrue(result.contains("[OpenAI:openai-mock]"));
        assertTrue(result.contains("mock response"));
    }
}
