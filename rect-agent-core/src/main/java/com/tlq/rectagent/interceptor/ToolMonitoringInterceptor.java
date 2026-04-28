package com.tlq.rectagent.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
public class ToolMonitoringInterceptor {

    public String getName() {
        return "ToolMonitoringInterceptor";
    }

    public <T> ToolCallResult<T> interceptToolCall(ToolCallRequest request, ToolCallHandler<T> handler) {
        String traceId = MDC.get("traceId");
        String toolName = request.toolName();
        String toolCallId = request.toolCallId();
        long startTime = System.currentTimeMillis();

        log.info("[{}] 开始执行工具: {}", traceId, toolName);
        log.debug("[{}] 工具入参: toolCallId={}, toolName={}",
                traceId, toolCallId, toolName);

        try {
            T result = handler.call();
            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] 工具执行成功: tool={}, 耗时={}ms", traceId, toolName, duration);

            return new ToolCallResult<>(result, toolCallId, toolName, null, duration, true);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] 工具执行失败: tool={}, 耗时={}ms, 错误: {}",
                    traceId, toolName, duration, e.getMessage());

            return new ToolCallResult<>(null, toolCallId, toolName, e.getMessage(), duration, false);
        }
    }

    public record ToolCallRequest(
            String toolCallId,
            String toolName,
            Object parameters
    ) {}

    public record ToolCallResult<T>(
            T result,
            String toolCallId,
            String toolName,
            String error,
            long durationMs,
            boolean success
    ) {}

    @FunctionalInterface
    public interface ToolCallHandler<T> {
        T call();
    }
}
