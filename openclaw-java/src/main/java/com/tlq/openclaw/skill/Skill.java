package com.tlq.openclaw.skill;

import java.util.Map;

public interface Skill {
    String getId();
    String getName();
    String getDescription();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    Object execute(Map<String, Object> parameters);
}