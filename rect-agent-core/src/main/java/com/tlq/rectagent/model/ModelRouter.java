package com.tlq.rectagent.model;

public class ModelRouter {
    private final ModelRegistry registry;
    private final ModelRoutingStrategy strategy;

    public ModelRouter(ModelRegistry registry, ModelRoutingStrategy strategy) {
        this.registry = registry;
        this.strategy = strategy;
    }

    public String route(String input) {
        if (registry == null || registry.isEmpty()) {
            throw new IllegalStateException("No model providers registered");
        }
        java.util.List<ModelProvider> providers = new java.util.ArrayList<>(registry.getAll());
        ModelProvider selected = strategy != null
                ? strategy.select(providers, new RoutingContext())
                : null;
        if (selected == null) {
            // fallback to first available
            selected = registry.getAll().stream().findFirst().orElse(null);
        }
        if (selected == null) throw new IllegalStateException("No provider available");
        try {
            return selected.call(input);
        } catch (Exception e) {
            throw new RuntimeException("Model invocation failed", e);
        }
    }
}
