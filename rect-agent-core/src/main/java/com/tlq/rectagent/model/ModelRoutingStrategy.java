package com.tlq.rectagent.model;

import java.util.List;

public interface ModelRoutingStrategy {
    ModelProvider select(List<ModelProvider> providers, RoutingContext ctx);
    int getPriority();
}
