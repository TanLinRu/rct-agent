package com.tlq.rectagent.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

public class CapabilityStrategyTest {
    @Test
    public void selectsProviderMatchingCapability() {
        ModelProviderConfig c1 = new ModelProviderConfig();
        c1.setName("c1"); c1.setEnabled(true); c1.setType("openai");
        c1.setModel("m"); c1.setCapability("chat");
        OpenAIProvider p1 = new OpenAIProvider(c1);

        ModelProviderConfig c2 = new ModelProviderConfig();
        c2.setName("c2"); c2.setEnabled(true); c2.setType("openai");
        c2.setModel("m"); c2.setCapability("embedding");
        OpenAIProvider p2 = new OpenAIProvider(c2);

        List<ModelProvider> providers = Arrays.asList(p1, p2);
        RoutingContext ctx = new RoutingContext();
        ctx.capability = "chat";

        CapabilityStrategy strategy = new CapabilityStrategy("chat");
        ModelProvider selected = strategy.select(providers, ctx);
        assertNotNull(selected);
        assertEquals("c1", selected.getName());
    }

    @Test
    public void returnsNullWhenNoMatch() {
        ModelProviderConfig c1 = new ModelProviderConfig();
        c1.setName("c1"); c1.setEnabled(true); c1.setType("openai");
        c1.setModel("m"); c1.setCapability("chat");
        OpenAIProvider p1 = new OpenAIProvider(c1);

        List<ModelProvider> providers = Arrays.asList(p1);
        RoutingContext ctx = new RoutingContext();
        ctx.capability = "image";

        CapabilityStrategy strategy = new CapabilityStrategy("image");
        ModelProvider selected = strategy.select(providers, ctx);
        assertNull(selected);
    }

    @Test
    public void returnsNullWhenAllDisabled() {
        ModelProviderConfig c1 = new ModelProviderConfig();
        c1.setName("c1"); c1.setEnabled(false); c1.setType("openai");
        c1.setModel("m"); c1.setCapability("chat");
        OpenAIProvider p1 = new OpenAIProvider(c1);

        List<ModelProvider> providers = Arrays.asList(p1);
        CapabilityStrategy strategy = new CapabilityStrategy("chat");
        ModelProvider selected = strategy.select(providers, new RoutingContext());
        assertNull(selected);
    }
}
