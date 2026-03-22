package com.tlq.rectagent.model;

public interface ModelProvider {
    String getName();
    boolean isEnabled();
    double getCostPerToken();
    String call(String input) throws Exception;
    // Priority for routing strategies (lower value = higher priority)
    default int getPriority() { return 0; }
    // Capability tag for CapabilityBased routing, e.g. "intent", "analysis", "code", "image"
    default String getCapability() { return null; }
}
