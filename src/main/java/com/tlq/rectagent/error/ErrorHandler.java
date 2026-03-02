package com.tlq.rectagent.error;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 错误处理器
 * 负责处理系统中的错误和异常，提供容错机制
 */
@Component
public class ErrorHandler {

    private final Map<String, String> errorMessages;

    public ErrorHandler() {
        this.errorMessages = new HashMap<>();
        initErrorMessages();
    }

    /**
     * 初始化错误消息
     */
    private void initErrorMessages() {
        errorMessages.put("API_KEY_ERROR", "API密钥无效或已过期");
        errorMessages.put("MODEL_ERROR", "模型调用失败");
        errorMessages.put("TOOL_ERROR", "工具执行失败");
        errorMessages.put("NETWORK_ERROR", "网络连接失败");
        errorMessages.put("DATA_ERROR", "数据处理错误");
        errorMessages.put("UNKNOWN_ERROR", "未知错误");
    }

    /**
     * 处理异常
     * @param e 异常
     * @param errorCode 错误代码
     * @return 错误信息
     */
    public String handleException(Exception e, String errorCode) {
        String errorMessage = errorMessages.getOrDefault(errorCode, errorMessages.get("UNKNOWN_ERROR"));
        System.err.println("错误: " + errorMessage + ", 详细信息: " + e.getMessage());
        return errorMessage;
    }

    /**
     * 带重试机制的执行方法
     * @param supplier 执行方法
     * @param maxRetries 最大重试次数
     * @param <T> 返回类型
     * @return 执行结果
     */
    public <T> T executeWithRetry(Supplier<T> supplier, int maxRetries) {
        int retries = 0;
        while (retries < maxRetries) {
            try {
                return supplier.get();
            } catch (Exception e) {
                retries++;
                System.err.println("执行失败，重试 " + retries + " 次: " + e.getMessage());
                if (retries >= maxRetries) {
                    throw new RuntimeException("达到最大重试次数", e);
                }
                try {
                    Thread.sleep(1000 * retries); // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
        throw new RuntimeException("执行失败");
    }

    /**
     * 安全执行方法
     * @param supplier 执行方法
     * @param defaultValue 默认值
     * @param <T> 返回类型
     * @return 执行结果或默认值
     */
    public <T> T safeExecute(Supplier<T> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (Exception e) {
            System.err.println("安全执行失败: " + e.getMessage());
            return defaultValue;
        }
    }

    /**
     * 记录错误
     * @param errorCode 错误代码
     * @param message 错误消息
     * @param e 异常
     */
    public void logError(String errorCode, String message, Exception e) {
        String errorMessage = errorMessages.getOrDefault(errorCode, errorCode);
        System.err.println("[错误] " + errorMessage + ": " + message);
        if (e != null) {
            e.printStackTrace();
        }
    }

    /**
     * 获取错误消息
     * @param errorCode 错误代码
     * @return 错误消息
     */
    public String getErrorMessage(String errorCode) {
        return errorMessages.getOrDefault(errorCode, errorMessages.get("UNKNOWN_ERROR"));
    }
}