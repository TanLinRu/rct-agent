package com.tlq.rectagent.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
public class ModelProcessInterceptor extends ModelInterceptor {

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        String traceId = MDC.get("traceId");
        int msgCount = request.getMessages() != null ? request.getMessages().size() : 0;
        log.info("[{}] 模型调用: 消息数={}", traceId, msgCount);

        long startTime = System.currentTimeMillis();
        ModelResponse response = handler.call(request);
        long duration = System.currentTimeMillis() - startTime;

        log.info("[{}] 模型响应: 耗时={}ms, 类型={}",
                traceId, duration,
                response != null ? response.getClass().getSimpleName() : "null");

        return response;
    }

    @Override
    public String getName() {
        return "ModelProcessInterceptor";
    }
}
