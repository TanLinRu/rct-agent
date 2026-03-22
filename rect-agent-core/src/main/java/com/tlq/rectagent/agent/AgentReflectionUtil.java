package com.tlq.rectagent.agent;

import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.Field;
import java.util.Map;

@Slf4j
public class AgentReflectionUtil {

    public static String getAgentName(Object agent) {
        if (agent == null) return "unknown";
        String override = mockNames.get(agent);
        if (override != null) return override;
        Class<?> clazz = agent.getClass();
        while (clazz != null) {
            try {
                Field[] fields = clazz.getDeclaredFields();
                for (Field field : fields) {
                    if ("name".equals(field.getName())) {
                        field.setAccessible(true);
                        Object value = field.get(agent);
                        if (value instanceof String && !((String) value).isEmpty()) {
                            return (String) value;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to get agent name via reflection from {}: {}", clazz.getName(), e.getMessage());
            }
            clazz = clazz.getSuperclass();
        }
        return "agent-" + agent.hashCode();
    }

    private static final Map<Object, String> mockNames = new java.util.WeakHashMap<>();

    public static void setMockAgentName(Object agent, String name) {
        mockNames.put(agent, name);
    }
}
