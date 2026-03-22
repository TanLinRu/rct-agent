package com.tlq.rectagent.model;

import java.util.Comparator;
import java.util.List;

public class CostBasedStrategy implements ModelRoutingStrategy {
    @Override
    public ModelProvider select(List<ModelProvider> providers, RoutingContext ctx) {
        return providers.stream()
                .filter(ModelProvider::isEnabled)
                .min(Comparator.comparingDouble(ModelProvider::getCostPerToken))
                .orElse(null);
    }
    @Override
    public int getPriority() { return 0; }
}
