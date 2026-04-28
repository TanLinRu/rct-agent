package com.tlq.rectagent.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.List;

@Slf4j
public class ModelProcessInterceptor {

    public String getName() {
        return "ModelProcessInterceptor";
    }

    public <T> T interceptModelCall(ModelCallContext context, ModelCallHandler<T> handler) {
        String traceId = MDC.get("traceId");
        int msgCount = context.messages() != null ? context.messages().size() : 0;
        log.info("[{}] 模型调用: 消息数={}", traceId, msgCount);

        long startTime = System.currentTimeMillis();
        T response = handler.call();
        long duration = System.currentTimeMillis() - startTime;

        log.info("[{}] 模型响应: 耗时={}ms, 类型={}",
                traceId, duration,
                response != null ? response.getClass().getSimpleName() : "null");

        return response;
    }

    public record ModelCallContext(
            Object model,
            String prompt,
            List<?> messages
    ) {}

    @FunctionalInterface
    public interface ModelCallHandler<T> {
        T call();
    }
}
