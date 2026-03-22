package com.tlq.rectagent.model;

public class ProviderResult {
    private final boolean success;
    private final String content;

    public ProviderResult(boolean success, String content) {
        this.success = success;
        this.content = content;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }
}
