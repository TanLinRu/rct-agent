package com.tlq.rectagent.context;

import com.tlq.rectagent.memory.ShortTermMemoryManager;
import com.tlq.rectagent.memory.LongTermMemoryManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 上下文管理器
 * 负责管理智能体之间的上下文传递和状态管理
 */
@Component
public class ContextManager {

    @Autowired
    private ShortTermMemoryManager shortTermMemoryManager;

    @Autowired
    private LongTermMemoryManager longTermMemoryManager;

    private final Map<String, Map<String, Object>> contextStore;

    public ContextManager() {
        this.contextStore = new HashMap<>();
    }

    /**
     * 创建上下文
     * @param contextId 上下文ID
     * @return 上下文映射
     */
    public Map<String, Object> createContext(String contextId) {
        Map<String, Object> context = new HashMap<>();
        contextStore.put(contextId, context);
        return context;
    }

    /**
     * 获取上下文
     * @param contextId 上下文ID
     * @return 上下文映射
     */
    public Map<String, Object> getContext(String contextId) {
        return contextStore.computeIfAbsent(contextId, k -> new HashMap<>());
    }

    /**
     * 更新上下文
     * @param contextId 上下文ID
     * @param key 键
     * @param value 值
     */
    public void updateContext(String contextId, String key, Object value) {
        Map<String, Object> context = getContext(contextId);
        context.put(key, value);
    }

    /**
     * 从上下文获取值
     * @param contextId 上下文ID
     * @param key 键
     * @return 值
     */
    public Object getContextValue(String contextId, String key) {
        Map<String, Object> context = getContext(contextId);
        return context.get(key);
    }

    /**
     * 合并上下文
     * @param sourceContextId 源上下文ID
     * @param targetContextId 目标上下文ID
     */
    public void mergeContext(String sourceContextId, String targetContextId) {
        Map<String, Object> sourceContext = getContext(sourceContextId);
        Map<String, Object> targetContext = getContext(targetContextId);
        targetContext.putAll(sourceContext);
    }

    /**
     * 清除上下文
     * @param contextId 上下文ID
     */
    public void clearContext(String contextId) {
        contextStore.remove(contextId);
    }

    /**
     * 保存上下文到短期记忆
     * @param sessionId 会话ID
     * @param contextId 上下文ID
     */
    public void saveContextToShortTermMemory(String sessionId, String contextId) {
        Map<String, Object> context = getContext(contextId);
        shortTermMemoryManager.saveSessionState(sessionId, context);
    }

    /**
     * 从短期记忆加载上下文
     * @param sessionId 会话ID
     * @param contextId 上下文ID
     */
    public void loadContextFromShortTermMemory(String sessionId, String contextId) {
        shortTermMemoryManager.getSessionState(sessionId).ifPresent(storeItem -> {
            Map<String, Object> context = getContext(contextId);
            // 简化实现，不加载状态
            System.out.println("加载会话状态: " + sessionId);
        });
    }

    /**
     * 保存上下文到长期记忆
     * @param userId 用户ID
     * @param contextId 上下文ID
     * @param key 键
     */
    public void saveContextToLongTermMemory(String userId, String contextId, String key) {
        Map<String, Object> context = getContext(contextId);
        Object value = context.get(key);
        if (value != null) {
            Map<String, Object> data = new HashMap<>();
            data.put(key, value);
            longTermMemoryManager.updateUserProfile(userId, data);
        }
    }

    /**
     * 从长期记忆加载上下文
     * @param userId 用户ID
     * @param contextId 上下文ID
     * @param key 键
     */
    public void loadContextFromLongTermMemory(String userId, String contextId, String key) {
        longTermMemoryManager.getUserProfile(userId).ifPresent(storeItem -> {
            Map<String, Object> context = getContext(contextId);
            // 简化实现，不加载状态
            System.out.println("加载用户配置文件: " + userId);
        });
    }
}