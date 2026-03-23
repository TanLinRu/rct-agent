package com.tlq.rectagent.agent;

import com.tlq.rectagent.agent.tools.AgentTool;
import com.tlq.rectagent.config.ChatModelFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class SupervisorAgent {

    private final ChatModelFactory chatModelFactory;
    private final AgentToolRegistry toolRegistry;

    @Autowired
    public SupervisorAgent(ChatModelFactory chatModelFactory, AgentToolRegistry toolRegistry) {
        this.chatModelFactory = chatModelFactory;
        this.toolRegistry = toolRegistry;
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
            List<AgentTool> tools = toolRegistry.getAgentTools();
            String output = input;
            
            for (AgentTool tool : tools) {
                log.debug("[{}] 执行工具: {}", traceId, tool.getName());
                output = tool.apply(output);
            }

            log.info("[{}] SupervisorAgent 处理完成: outputLength={}", traceId,
                    output != null ? output.length() : 0);

            return new SupervisorResult(output, traceId, "tools_executed", List.of());

        } catch (Exception e) {
            log.error("[{}] SupervisorAgent 执行失败: {}", traceId, e.getMessage(), e);
            return new SupervisorResult("执行失败: " + e.getMessage(), traceId, "error", null);
        }
    }

    public record SupervisorResult(
            String content,
            String traceId,
            String selectedAgent,
            List<String> routeHistory
    ) {}
}
