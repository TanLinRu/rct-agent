package com.tlq.rectagent.agent;

import com.tlq.rectagent.model.ModelProvider;
import com.tlq.rectagent.model.ModelRegistry;
import com.tlq.rectagent.model.ModelRouter;
import com.tlq.rectagent.model.CostBasedStrategy;
import com.tlq.rectagent.agent.tools.AgentTool;
import com.tlq.rectagent.agent.tools.IntentTool;
import com.tlq.rectagent.agent.tools.AnalysisTool;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SupervisorAgentPerfTest {
    private static final int WARMUP = 5;
    private static final int ITERATIONS = 100;
    private static final int CONCURRENCY = 10;

    @Test
    public void singleInvocationLatency() {
        ModelRegistry reg = buildRegistry();
        ModelRouter router = new ModelRouter(reg, new CostBasedStrategy());
        SupervisorAgent supervisor = buildSupervisor(router);

        for (int i = 0; i < WARMUP; i++) supervisor.invoke("warmup");
        long total = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            supervisor.invoke("latency-test-" + i);
            total += (System.nanoTime() - start);
        }
        double avgMs = total / 1_000_000.0 / ITERATIONS;
        System.out.printf("Single invocation avg latency: %.3f ms%n", avgMs);
        assertTrue("Average latency should be under 10ms for mock", avgMs < 10.0);
    }

    @Test
    public void throughputUnderLoad() throws Exception {
        ModelRegistry reg = buildRegistry();
        ModelRouter router = new ModelRouter(reg, new CostBasedStrategy());
        SupervisorAgent supervisor = buildSupervisor(router);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ITERATIONS);
        AtomicInteger success = new AtomicInteger(0);

        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    start.await();
                    supervisor.invoke("throughput-" + id);
                    success.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        pool.shutdown();

        double throughput = ITERATIONS * 1000.0 / elapsedMs;
        System.out.printf("Throughput: %.2f invocations/sec (%d/%d in %d ms)%n",
                throughput, success.get(), ITERATIONS, elapsedMs);
        assertEquals(ITERATIONS, success.get());
        assertTrue("Throughput should be > 50 req/s", throughput > 50);
    }

    @Test
    public void latencyUnderLoad() throws Exception {
        ModelRegistry reg = buildRegistry();
        final ModelRouter router = new ModelRouter(reg, new CostBasedStrategy());
        final SupervisorAgent supervisor = buildSupervisor(router);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    long t0 = System.nanoTime();
                    supervisor.invoke("load-" + System.nanoTime());
                    latencies.add(System.nanoTime() - t0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        double avg = latencies.isEmpty() ? 0 : latencies.stream().mapToLong(Long::longValue).average().getAsDouble() / 1_000_000.0;
        double p99 = latencies.isEmpty() ? 0 : latencies.stream().sorted()
                .skip((long)(latencies.size() * 0.99))
                .findFirst().orElse(0L) / 1_000_000.0;
        System.out.printf("Latency under load: avg=%.3fms p99=%.3fms%n", avg, p99);
        assertTrue("Average latency under load should be under 50ms", avg < 50.0);
    }

    @Test
    public void registryWithMultipleProvidersScales() {
        ModelRegistry reg = new ModelRegistry();
        for (int i = 0; i < 5; i++) {
            final int id = i;
            reg.register(new ModelProvider() {
                @Override public String getName() { return "p" + id; }
                @Override public boolean isEnabled() { return true; }
                @Override public double getCostPerToken() { return 0.001 * (id + 1); }
                @Override public String call(String input) { return "p" + id + ":" + input; }
                @Override public int getPriority() { return id; }
            });
        }
        ModelRouter router = new ModelRouter(reg, new CostBasedStrategy());

        long total = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long t0 = System.nanoTime();
            router.route("test");
            total += (System.nanoTime() - t0);
        }
        double avgMs = total / 1_000_000.0 / ITERATIONS;
        System.out.printf("Router with 5 providers avg: %.3f ms%n", avgMs);
        assertTrue("Router avg should be < 5ms", avgMs < 5.0);
    }

    private ModelRegistry buildRegistry() {
        ModelRegistry reg = new ModelRegistry();
        reg.register(new ModelProvider() {
            @Override public String getName() { return "fast"; }
            @Override public boolean isEnabled() { return true; }
            @Override public double getCostPerToken() { return 0.001; }
            @Override public String call(String input) { return "OUT:" + input; }
            @Override public int getPriority() { return 0; }
        });
        return reg;
    }

    private SupervisorAgent buildSupervisor(ModelRouter router) {
        IntentTool intent = new IntentTool("intent");
        AnalysisTool analysis = new AnalysisTool("analysis");
        return new SupervisorAgent(router, Arrays.asList((AgentTool)intent, (AgentTool)analysis));
    }
}
