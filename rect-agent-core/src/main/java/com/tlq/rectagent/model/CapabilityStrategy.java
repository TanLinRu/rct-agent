package com.tlq.rectagent.model;

import java.util.Comparator;
import java.util.List;

public class CapabilityStrategy implements ModelRoutingStrategy {
    private final String requiredCapability;

    public CapabilityStrategy(String requiredCapability) {
        this.requiredCapability = requiredCapability;
    }

    @Override
    public ModelProvider select(List<ModelProvider> providers, RoutingContext ctx) {
        String cap = (ctx != null && ctx.capability != null) ? ctx.capability : requiredCapability;
        return providers.stream()
                .filter(ModelProvider::isEnabled)
                .filter(p -> {
                    String c = p.getCapability();
                    return c != null && (c.equalsIgnoreCase(cap) || c.equalsIgnoreCase("any"));
                })
                .min(Comparator.comparingDouble(ModelProvider::getCostPerToken))
                .orElse(null);
    }

    @Override
    public int getPriority() { return 2; }
}
