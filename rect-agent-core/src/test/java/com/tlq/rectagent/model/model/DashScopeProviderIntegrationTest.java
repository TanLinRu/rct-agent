package com.tlq.rectagent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class DashScopeProviderIntegrationTest {
    @Test
    public void dashScopeCallsEndpointAndParsesResponse() throws Exception {
        // start a small HTTP server to simulate DashScope endpoint
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/dash", exchange -> {
            String resp = "{\"text\": \"dash reply from server\"}";
            exchange.sendResponseHeaders(200, resp.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(resp.getBytes(StandardCharsets.UTF_8));
            os.close();
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        int port = server.getAddress().getPort();
        String endpoint = "http://localhost:" + port + "/dash";

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("dashtest");
        cfg.setEnabled(true);
        cfg.setType("dashscope");
        cfg.setModel("default");
        cfg.setCostPerToken(0.1);
        cfg.setEndpoints(java.util.Arrays.asList(endpoint, endpoint));
        cfg.setMock(false);
        cfg.setMaxRetries(2);
        cfg.setRetryDelayMs(100);

        DashScopeProvider provider = new DashScopeProvider(cfg);
        String result = provider.call("hello");
        assertTrue(result.contains("dash reply from server"));

        server.stop(0);
    }
}
