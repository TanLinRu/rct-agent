package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;

public class RoundRobinStrategyTest {
    @Test
    public void roundRobinsThroughProviders() {
        ModelProviderConfig c1 = new ModelProviderConfig();
        c1.setName("p1"); c1.setEnabled(true); c1.setType("dashscope");
        c1.setModel("m"); c1.setPriority(1);
        DashScopeProvider p1 = new DashScopeProvider(c1);

        ModelProviderConfig c2 = new ModelProviderConfig();
        c2.setName("p2"); c2.setEnabled(true); c2.setType("dashscope");
        c2.setModel("m"); c2.setPriority(2);
        DashScopeProvider p2 = new DashScopeProvider(c2);

        RoundRobinStrategy strategy = new RoundRobinStrategy();
        assertEquals("p1", strategy.select(Arrays.asList(p1, p2), new RoutingContext()).getName());
        assertEquals("p2", strategy.select(Arrays.asList(p1, p2), new RoutingContext()).getName());
        assertEquals("p1", strategy.select(Arrays.asList(p1, p2), new RoutingContext()).getName());
    }
}
