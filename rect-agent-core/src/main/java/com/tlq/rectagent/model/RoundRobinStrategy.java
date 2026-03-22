package com.tlq.rectagent.model;

import java.util.List;

public class RoundRobinStrategy implements ModelRoutingStrategy {
    private int cursor = 0;

    @Override
    public ModelProvider select(List<ModelProvider> providers, RoutingContext ctx) {
        List<ModelProvider> enabled = providers.stream()
                .filter(ModelProvider::isEnabled)
                .toList();
        if (enabled.isEmpty()) return null;
        int idx = cursor % enabled.size();
        cursor++;
        return enabled.get(idx);
    }

    @Override
    public int getPriority() { return 2; }
}
