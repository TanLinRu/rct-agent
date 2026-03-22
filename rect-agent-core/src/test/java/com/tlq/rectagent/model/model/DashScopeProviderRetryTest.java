package com.tlq.rectagent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class DashScopeProviderRetryTest {
    @Test
    public void retrySequenceSucceeds() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/dash", exchange -> {
            int n = counter.incrementAndGet();
            if (n <= 2) {
                String resp = "err";
                exchange.sendResponseHeaders(500, resp.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp.getBytes());
                }
            } else {
                String resp = "{\"text\": \"retry success\"}";
                exchange.sendResponseHeaders(200, resp.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp.getBytes());
                }
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();
        String endpoint = "http://localhost:" + port + "/dash";

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("dashretry");
        cfg.setEnabled(true);
        cfg.setType("dashscope");
        cfg.setModel("default");
        cfg.setCostPerToken(0.1);
        cfg.setEndpoints(java.util.Arrays.asList(endpoint, endpoint));
        cfg.setMock(false);
        cfg.setMaxRetries(5);
        cfg.setRetryDelayMs(10);

        DashScopeProvider provider = new DashScopeProvider(cfg);
        String result = provider.call("ping");
        assertEquals("retry success", result);

        server.stop(0);
    }
}
