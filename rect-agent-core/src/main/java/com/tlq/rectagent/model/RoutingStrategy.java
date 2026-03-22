package com.tlq.rectagent.model;

import java.util.List;

public interface RoutingStrategy {
    ModelProvider select(List<ModelProvider> providers, RoutingContext ctx);
}
