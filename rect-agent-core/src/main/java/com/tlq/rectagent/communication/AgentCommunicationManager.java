package com.tlq.rectagent.communication;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
public class AgentCommunicationManager {

    private final Map<String, BlockingQueue<AgentMessage>> messageQueues;

    public AgentCommunicationManager() {
        this.messageQueues = new HashMap<>();
    }

    public void sendMessage(String recipientAgent, AgentMessage message) {
        String traceId = MDC.get("traceId");
        BlockingQueue<AgentMessage> queue = messageQueues.computeIfAbsent(recipientAgent, k -> new LinkedBlockingQueue<>());
        boolean offered = queue.offer(message);
        int queueSize = queue.size();
        int contentLen = message.getContent() != null ? message.getContent().length() : 0;
        log.info("[{}] 消息发送: from={}, to={}, type={}, 内容长度={}, 队列深度={}, 成功={}", 
                traceId, message.getSender(), recipientAgent, message.getMessageType(), 
                contentLen, queueSize, offered);
        log.debug("[{}] 消息详情: {}", traceId, message);
    }

    public AgentMessage receiveMessage(String agentName) {
        String traceId = MDC.get("traceId");
        BlockingQueue<AgentMessage> queue = messageQueues.get(agentName);
        if (queue != null) {
            AgentMessage msg = queue.poll();
            if (msg != null) {
                log.info("[{}] 消息接收: from={}, to={}, type={}, 剩余队列={}", 
                        traceId, msg.getSender(), agentName, msg.getMessageType(), queue.size());
                log.debug("[{}] 接收消息详情: {}", traceId, msg);
                return msg;
            }
            log.debug("[{}] 无消息待接收: agent={}", traceId, agentName);
        }
        return null;
    }

    public AgentMessage blockingReceive(String agentName) throws InterruptedException {
        String traceId = MDC.get("traceId");
        BlockingQueue<AgentMessage> queue = messageQueues.computeIfAbsent(agentName, k -> new LinkedBlockingQueue<>());
        log.debug("[{}] 阻塞等待消息: agent={}", traceId, agentName);
        AgentMessage msg = queue.take();
        log.info("[{}] 阻塞消息接收: from={}, to={}, type={}", 
                traceId, msg.getSender(), agentName, msg.getMessageType());
        return msg;
    }

    public boolean hasMessage(String agentName) {
        BlockingQueue<AgentMessage> queue = messageQueues.get(agentName);
        boolean has = queue != null && !queue.isEmpty();
        log.trace("检查消息队列: agent={}, 有待处理消息={}", agentName, has);
        return has;
    }

    public void clearMessages(String agentName) {
        String traceId = MDC.get("traceId");
        BlockingQueue<AgentMessage> queue = messageQueues.get(agentName);
        if (queue != null) {
            int cleared = queue.size();
            queue.clear();
            log.info("[{}] 清空消息队列: agent={}, 清除数量={}", traceId, agentName, cleared);
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