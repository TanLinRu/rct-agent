package com.tlq.rectagent.communication;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 智能体通信管理器
 * 负责处理智能体之间的消息传递和通信
 */
@Component
public class AgentCommunicationManager {

    private final Map<String, BlockingQueue<AgentMessage>> messageQueues;

    public AgentCommunicationManager() {
        this.messageQueues = new HashMap<>();
    }

    /**
     * 发送消息给指定智能体
     * @param recipientAgent 接收智能体名称
     * @param message 消息内容
     */
    public void sendMessage(String recipientAgent, AgentMessage message) {
        BlockingQueue<AgentMessage> queue = messageQueues.computeIfAbsent(recipientAgent, k -> new LinkedBlockingQueue<>());
        queue.offer(message);
    }

    /**
     * 接收指定智能体的消息
     * @param agentName 智能体名称
     * @return 消息，如果没有消息则返回null
     */
    public AgentMessage receiveMessage(String agentName) {
        BlockingQueue<AgentMessage> queue = messageQueues.get(agentName);
        if (queue != null) {
            return queue.poll();
        }
        return null;
    }

    /**
     * 阻塞式接收消息
     * @param agentName 智能体名称
     * @return 消息
     * @throws InterruptedException 中断异常
     */
    public AgentMessage blockingReceive(String agentName) throws InterruptedException {
        BlockingQueue<AgentMessage> queue = messageQueues.computeIfAbsent(agentName, k -> new LinkedBlockingQueue<>());
        return queue.take();
    }

    /**
     * 检查是否有未处理的消息
     * @param agentName 智能体名称
     * @return 是否有消息
     */
    public boolean hasMessage(String agentName) {
        BlockingQueue<AgentMessage> queue = messageQueues.get(agentName);
        return queue != null && !queue.isEmpty();
    }

    /**
     * 清空智能体的消息队列
     * @param agentName 智能体名称
     */
    public void clearMessages(String agentName) {
        BlockingQueue<AgentMessage> queue = messageQueues.get(agentName);
        if (queue != null) {
            queue.clear();
        }
    }

    /**
     * 消息类
     */
    public static class AgentMessage {
        private final String sender;        // 发送者
        private final String recipient;     // 接收者
        private final String content;       // 消息内容
        private final String messageType;   // 消息类型
        private final Map<String, Object> metadata; // 元数据

        public AgentMessage(String sender, String recipient, String content, String messageType) {
            this.sender = sender;
            this.recipient = recipient;
            this.content = content;
            this.messageType = messageType;
            this.metadata = new HashMap<>();
        }

        public AgentMessage(String sender, String recipient, String content, String messageType, Map<String, Object> metadata) {
            this.sender = sender;
            this.recipient = recipient;
            this.content = content;
            this.messageType = messageType;
            this.metadata = metadata;
        }

        // Getters
        public String getSender() { return sender; }
        public String getRecipient() { return recipient; }
        public String getContent() { return content; }
        public String getMessageType() { return messageType; }
        public Map<String, Object> getMetadata() { return metadata; }

        @Override
        public String toString() {
            return "AgentMessage{" +
                    "sender='" + sender + '\'' +
                    ", recipient='" + recipient + '\'' +
                    ", messageType='" + messageType + '\'' +
                    ", content='" + content.substring(0, Math.min(50, content.length())) + (content.length() > 50 ? "..." : "") + '\'' +
                    '}';
        }
    }
}