package com.tlq.rectagent.model.config;

import lombok.Data;

@Data
public class ProviderConfig {
    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private int timeout = 30000;
    private int maxRetries = 2;
}
