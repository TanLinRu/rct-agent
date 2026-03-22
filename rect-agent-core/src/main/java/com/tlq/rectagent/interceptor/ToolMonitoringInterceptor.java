package com.tlq.rectagent.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
public class ToolMonitoringInterceptor extends ToolInterceptor {
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String traceId = MDC.get("traceId");
        String toolName = request.getToolName();
        long startTime = System.currentTimeMillis();

        log.info("[{}] 开始执行工具: {}", traceId, toolName);
        log.debug("[{}] 工具入参: toolCallId={}, toolName={}", 
                traceId, request.getToolCallId(), toolName);

        try {
            ToolCallResponse response = handler.call(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] 工具执行成功: tool={}, 耗时={}ms", traceId, toolName, duration);

            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] 工具执行失败: tool={}, 耗时={}ms, 错误: {}", 
                    traceId, toolName, duration, e.getMessage());

            return ToolCallResponse.of(
                    request.getToolCallId(),
                    request.getToolName(),
                    "工具执行失败: " + e.getMessage()
            );
        }
    }

    @Override
    public String getName() {
        return "ToolMonitoringInterceptor";
    }
}
