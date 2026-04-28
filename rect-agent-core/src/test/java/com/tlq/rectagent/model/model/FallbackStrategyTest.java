package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FallbackStrategyTest {
    @Test
    public void selectsProviderWithHighestPriority() {
        ModelProviderConfig p1 = new ModelProviderConfig();
        p1.setName("p1"); p1.setEnabled(true); p1.setType("openai");
        p1.setModel("m"); p1.setCostPerToken(0.5); p1.setPriority(5);
        OpenAIProvider d1 = new OpenAIProvider(p1);

        ModelProviderConfig p2 = new ModelProviderConfig();
        p2.setName("p2"); p2.setEnabled(true); p2.setType("openai");
        p2.setModel("m"); p2.setCostPerToken(0.2); p2.setPriority(1);
        OpenAIProvider d2 = new OpenAIProvider(p2);

        ModelRegistry reg = new ModelRegistry();
        reg.register(d1); reg.register(d2);

        FallbackStrategy strategy = new FallbackStrategy();
        ModelProvider selected = strategy.select(new ArrayList<>(reg.getAll()), new RoutingContext());
        assertNotNull(selected);
        assertEquals("p2", selected.getName());
    }
}
