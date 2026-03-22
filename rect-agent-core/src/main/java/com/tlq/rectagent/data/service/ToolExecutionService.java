package com.tlq.rectagent.data.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tlq.rectagent.data.entity.ToolExecution;
import com.tlq.rectagent.data.mapper.ToolExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionService extends ServiceImpl<ToolExecutionMapper, ToolExecution> {

    @Transactional
    public ToolExecution createExecution(String messageId, String mcpServerName, 
                                          String toolName, String requestPayload) {
        ToolExecution execution = new ToolExecution();
        execution.setMessageId(messageId);
        execution.setMcpServerName(mcpServerName);
        execution.setToolName(toolName);
        execution.setRequestPayload(requestPayload);
        execution.setStatus(ToolExecution.Status.PENDING.name());
        this.save(execution);
        log.info("Created tool execution: {} for message: {}", execution.getExecutionId(), messageId);
        return execution;
    }

    @Transactional
    public void updateExecutionSuccess(String executionId, String responsePayload, long durationMs) {
        this.update(new LambdaUpdateWrapper<ToolExecution>()
                .eq(ToolExecution::getExecutionId, executionId)
                .set(ToolExecution::getStatus, ToolExecution.Status.SUCCESS.name())
                .set(ToolExecution::getResponsePayload, responsePayload)
                .set(ToolExecution::getDurationMs, durationMs));
        int respLen = responsePayload != null ? responsePayload.length() : 0;
        log.info("工具执行成功: executionId={}, 耗时={}ms, 响应长度={}", executionId, durationMs, respLen);
        log.debug("工具响应详情: executionId={}, 响应={}", executionId, responsePayload);
    }

    @Transactional
    public void updateExecutionFailed(String executionId, String errorMessage) {
        this.update(new LambdaUpdateWrapper<ToolExecution>()
                .eq(ToolExecution::getExecutionId, executionId)
                .set(ToolExecution::getStatus, ToolExecution.Status.FAILED.name())
                .set(ToolExecution::getErrorMessage, errorMessage));
        log.warn("工具执行失败: executionId={}, 错误={}", executionId, errorMessage);
    }

    @Transactional
    public void updateExecutionTimeout(String executionId) {
        this.update(new LambdaUpdateWrapper<ToolExecution>()
                .eq(ToolExecution::getExecutionId, executionId)
                .set(ToolExecution::getStatus, ToolExecution.Status.TIMEOUT.name()));
        log.warn("工具执行超时: executionId={}", executionId);
    }

    public List<ToolExecution> getExecutionsByMessageId(String messageId) {
        return this.list(new LambdaQueryWrapper<ToolExecution>()
                .eq(ToolExecution::getMessageId, messageId)
                .orderByAsc(ToolExecution::getCreatedAt));
    }

    public List<ToolExecution> getExecutionsByMcpServer(String mcpServerName) {
        return this.list(new LambdaQueryWrapper<ToolExecution>()
                .eq(ToolExecution::getMcpServerName, mcpServerName));
    }
}
