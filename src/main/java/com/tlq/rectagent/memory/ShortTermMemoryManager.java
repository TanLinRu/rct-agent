package com.tlq.rectagent.memory;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 短期记忆管理器
 * 负责管理对话历史和会话状态
 */
@Component
public class ShortTermMemoryManager {

    private final MemoryStore memoryStore;
    private final Map<String, List<String>> sessionHistory;

    public ShortTermMemoryManager() {
        this.memoryStore = new MemoryStore();
        this.sessionHistory = new HashMap<>();
    }

    /**
     * 添加对话记录到短期记忆
     * @param sessionId 会话ID
     * @param message 消息内容
     * @param role 角色（user/assistant）
     */
    public void addMessage(String sessionId, String message, String role) {
        if (!sessionHistory.containsKey(sessionId)) {
            sessionHistory.put(sessionId, new ArrayList<>());
        }
        sessionHistory.get(sessionId).add(role + ": " + message);
        
        // 限制历史记录长度，保持在合理范围内
        List<String> history = sessionHistory.get(sessionId);
        if (history.size() > 50) {
            history.subList(0, history.size() - 50).clear();
        }
    }

    /**
     * 获取会话历史
     * @param sessionId 会话ID
     * @return 会话历史列表
     */
    public List<String> getSessionHistory(String sessionId) {
        return sessionHistory.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * 保存会话状态到内存存储
     * @param sessionId 会话ID
     * @param state 会话状态
     */
    public void saveSessionState(String sessionId, Map<String, Object> state) {
        List<String> namespace = List.of("sessions", sessionId);
        StoreItem item = StoreItem.of(namespace, "state", state);
        memoryStore.putItem(item);
    }

    /**
     * 获取会话状态
     * @param sessionId 会话ID
     * @return 会话状态
     */
    public Optional<StoreItem> getSessionState(String sessionId) {
        List<String> namespace = List.of("sessions", sessionId);
        return memoryStore.getItem(namespace, "state");
    }

    /**
     * 清除会话记忆
     * @param sessionId 会话ID
     */
    public void clearSessionMemory(String sessionId) {
        sessionHistory.remove(sessionId);
        List<String> namespace = List.of("sessions", sessionId);
        memoryStore.deleteItem(namespace, "state");
    }

    /**
     * 获取会话历史摘要
     * @param sessionId 会话ID
     * @return 历史摘要
     */
    public String getSessionSummary(String sessionId) {
        List<String> history = getSessionHistory(sessionId);
        if (history.isEmpty()) {
            return "无历史记录";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("会话历史摘要：\n");
        for (int i = Math.max(0, history.size() - 5); i < history.size(); i++) {
            summary.append(history.get(i)).append("\n");
        }
        return summary.toString();
    }
}