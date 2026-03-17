package com.tlq.openclaw.tool;

import java.util.Map;

public interface Tool {
    String getId();
    String getName();
    String getDescription();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    Object execute(Map<String, Object> parameters);
}