package com.tlq.rectagent.model;

import java.util.ArrayList;
import java.util.List;

public class ModelProviderFactory {
    public static ModelProvider createFromConfig(ModelProviderConfig cfg) {
        if (cfg == null || !cfg.isEnabled()) return null;
        switch (cfg.getType() == null ? "dashscope" : cfg.getType().toLowerCase()) {
            case "openai":
                return new OpenAIProvider(cfg);
            case "anthropic":
                return new AnthropicProvider(cfg);
            case "dashscope":
            default:
                return new DashScopeProvider(cfg);
        }
    }

    public static List<ModelProvider> createFromConfigs(List<ModelProviderConfig> configs) {
        List<ModelProvider> list = new ArrayList<>();
        if (configs == null) return list;
        for (ModelProviderConfig c : configs) {
            ModelProvider p = createFromConfig(c);
            if (p != null) list.add(p);
        }
        return list;
    }
}
