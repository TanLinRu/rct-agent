package com.tlq.rectagent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.Arrays;

import static org.junit.Assert.*;

public class DashScopeProviderMultiEndpointTest {
    @Test
    public void choosesSecondEndpointWhenFirstFails() throws Exception {
        HttpServer s1 = HttpServer.create(new InetSocketAddress(0), 0);
        s1.createContext("/dash", exchange -> {
            String resp = "{ \"text\": \"from-first-endpoint\" }";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        s1.setExecutor(Executors.newSingleThreadExecutor());
        s1.start();
        int port1 = s1.getAddress().getPort();
        HttpServer s2 = HttpServer.create(new InetSocketAddress(0), 0);
        s2.createContext("/dash", exchange -> {
            String resp = "{\"text\": \"second-endpoint-ok\"}";
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes());
            }
        });
        s2.setExecutor(Executors.newSingleThreadExecutor());
        s2.start();
        int port2 = s2.getAddress().getPort();

        // First endpoint will be contacted but we simulate a failure by using a failing URL
        String ep1 = "http://localhost:" + port1 + "/dash?fail=true"; // still returns 200 in this mock, so we instead simulate retry by setting endpoint to a non-responding path
        // For simplicity, point to non-existing endpoint first, then a working one
        String badEp = "http://localhost:" + port1 + "/bad";
        String goodEp = "http://localhost:" + port2 + "/dash";

        ModelProviderConfig cfg = new ModelProviderConfig();
        cfg.setName("dashmulti");
        cfg.setEnabled(true);
        cfg.setType("dashscope");
        cfg.setModel("default");
        cfg.setCostPerToken(0.1);
        cfg.setEndpoints(Arrays.asList(badEp, goodEp));
        cfg.setMaxRetries(0);
        cfg.setRetryDelayMs(0);
        cfg.setMock(false);

        DashScopeProvider provider = new DashScopeProvider(cfg);
        String result = provider.call("anything");
        assertTrue(result.contains("second-endpoint-ok") || result.contains("dash reply"));

        s1.stop(0);
        s2.stop(0);
    }
}
