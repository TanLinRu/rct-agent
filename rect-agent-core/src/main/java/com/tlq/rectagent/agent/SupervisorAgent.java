package com.tlq.rectagent.agent;

import com.tlq.rectagent.agent.tools.AgentTool;
import com.tlq.rectagent.config.ChatModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SupervisorAgent {

    private static final int MAX_AGENT_CALLS = 3;
    private static final int MAX_RETRIES = 1;
    private static final String FINISH = "FINISH";

    private final ChatModelFactory chatModelFactory;
    private final AgentToolRegistry toolRegistry;
    private SupervisorState state;

    @Autowired
    public SupervisorAgent(ChatModelFactory chatModelFactory, AgentToolRegistry toolRegistry) {
        this.chatModelFactory = chatModelFactory;
        this.toolRegistry = toolRegistry;
        this.state = new SupervisorState();
    }

    public String invoke(String input) {
        return invoke(input, null, null);
    }

    public String invoke(String input, String sessionId, String userId) {
        return invokeWithMetadata(input, sessionId, userId).content();
    }

    public SupervisorResult invokeWithMetadata(String input, String sessionId, String userId) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[{}] SupervisorAgent 开始处理: inputLength={}", traceId, 
                input != null ? input.length() : 0);

        try {
            state.reset();
            state.setInput(input);
            state.setSessionId(sessionId);
            state.setUserId(userId);

            List<AgentTool> tools = toolRegistry.getAgentTools();
            
            if (tools.isEmpty()) {
                log.warn("[{}] 未注册任何工具", traceId);
                return new SupervisorResult(input, traceId, "no_tools", List.of());
            }

            String output = input;
            int agentCallCount = 0;
            String lastCalledAgent = null;
            List<String> routeHistory = new ArrayList<>();

            while (agentCallCount < MAX_AGENT_CALLS) {
                AgentTool selectedTool = selectNextTool(tools, state, traceId);
                
                if (selectedTool == null || FINISH.equals(selectedTool.getName())) {
                    log.info("[{}] 任务完成，退出循环", traceId);
                    break;
                }

                if (state.isAgentCalled(selectedTool.getName()) && agentCallCount > 0) {
                    log.debug("[{}] Agent {} 已调用过，选择其他Agent", traceId, selectedTool.getName());
                    continue;
                }

                log.debug("[{}] 执行工具: {}", traceId, selectedTool.getName());
                output = selectedTool.apply(output);
                
                state.recordAgentCall(selectedTool.getName());
                routeHistory.add(selectedTool.getName());
                lastCalledAgent = selectedTool.getName();
                agentCallCount++;

                if (isTaskComplete(output, traceId)) {
                    log.info("[{}] 任务完成", traceId);
                    break;
                }
            }

            log.info("[{}] SupervisorAgent 处理完成: outputLength={}, 调用次数={}", traceId,
                    output != null ? output.length() : 0, agentCallCount);

            return new SupervisorResult(output, traceId, lastCalledAgent, routeHistory);

        } catch (Exception e) {
            log.error("[{}] SupervisorAgent 执行失败: {}", traceId, e.getMessage(), e);
            return new SupervisorResult(
                    "执行失败: " + e.getMessage(), 
                    traceId, 
                    "error", 
                    null
            );
        }
    }

    private AgentTool selectNextTool(List<AgentTool> tools, SupervisorState state, String traceId) {
        for (AgentTool tool : tools) {
            if (!state.isAgentCalled(tool.getName())) {
                return tool;
            }
        }
        return null;
    }

    private boolean isTaskComplete(String output, String traceId) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        
        if (output.contains("FINISH") || output.contains("完成")) {
            return true;
        }
        
        if (output.length() > 100 && !output.contains("错误") && !output.contains("失败")) {
            return true;
        }
        
        return false;
    }

    public record SupervisorResult(
            String content,
            String traceId,
            String selectedAgent,
            List<String> routeHistory
    ) {}

    private static class SupervisorState {
        private String input;
        private String sessionId;
        private String userId;
        private final Set<String> calledAgents = new HashSet<>();
        private final List<String> routeHistory = new ArrayList<>();
        private int agentCallCount = 0;

        public void reset() {
            calledAgents.clear();
            routeHistory.clear();
            agentCallCount = 0;
        }

        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public boolean isAgentCalled(String agentName) {
            return calledAgents.contains(agentName);
        }

        public void recordAgentCall(String agentName) {
            calledAgents.add(agentName);
            routeHistory.add(agentName);
            agentCallCount++;
        }

        public int getAgentCallCount() {
            return agentCallCount;
        }

        public List<String> getRouteHistory() {
            return routeHistory;
        }
    }
}
