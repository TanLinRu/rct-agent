package com.tlq.rectagent.agent;

import org.junit.Test;
import static org.junit.Assert.*;

public class AgentReflectionUtilTest {

    private static class FakeAgent {
        private final String name;
        FakeAgent(String name) { this.name = name; }
        String getName() { return name; }
    }

    @Test
    public void returnsAgentFieldName() {
        FakeAgent agent = new FakeAgent("intent_recognition_agent");
        String name = AgentReflectionUtil.getAgentName(agent);
        assertEquals("intent_recognition_agent", name);
    }

    @Test
    public void fallsBackToHashCode() {
        FakeAgent agent = new FakeAgent("");
        String name = AgentReflectionUtil.getAgentName(agent);
        assertTrue(name.startsWith("agent-"));
    }

    @Test
    public void handlesNullGracefully() {
        assertEquals("unknown", AgentReflectionUtil.getAgentName(null));
    }
}
