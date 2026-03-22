package com.tlq.rectagent.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ModelRegistry {
    private final Map<String, ModelProvider> registry = new HashMap<>();

    public void register(ModelProvider provider) {
        if (provider == null) return;
        registry.put(provider.getName(), provider);
    }

    public Optional<ModelProvider> get(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    public boolean isEmpty() {
        return registry.isEmpty();
    }

    public Collection<ModelProvider> getAll() {
        return registry.values();
    }

    // Convenience for programmatic bootstrap
    public void clear() { registry.clear(); }
    public void addAll(Collection<ModelProvider> providers) {
        if (providers == null) return;
        for (ModelProvider p : providers) {
            register(p);
        }
    }
}
