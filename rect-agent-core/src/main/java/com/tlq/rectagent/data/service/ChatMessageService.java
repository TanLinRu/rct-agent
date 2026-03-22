package com.tlq.rectagent.data.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tlq.rectagent.data.entity.ChatMessage;
import com.tlq.rectagent.data.entity.ConversationCheckpoint;
import com.tlq.rectagent.data.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageService extends ServiceImpl<ChatMessageMapper, ChatMessage> {

    @Autowired
    private ConversationCheckpointService checkpointService;

    public ChatMessage saveMessage(String sessionId, int turnIndex, String role, 
                                   String contentRaw, String contentProcessed) {
        try {
            ChatMessage message = new ChatMessage();
            message.setSessionId(sessionId);
            message.setTurnIndex(turnIndex);
            message.setRole(role);
            message.setContentRaw(contentRaw);
            message.setContentProcessed(contentProcessed);
            this.save(message);
            int rawLen = contentRaw != null ? contentRaw.length() : 0;
            int processedLen = contentProcessed != null ? contentProcessed.length() : 0;
            log.info("保存消息: messageId={}, sessionId={}, role={}, turn={}, 原始长度={}, 处理后长度={}", 
                    message.getMessageId(), sessionId, role, turnIndex, rawLen, processedLen);
            log.debug("消息内容: messageId={}, 原始={}, 处理后={}", 
                    message.getMessageId(), contentRaw, contentProcessed);
            return message;
        } catch (Exception e) {
            log.error("保存消息失败: sessionId={}, role={}, turn={}, 错误: {}", 
                    sessionId, role, turnIndex, e.getMessage(), e);
            throw e;
        }
    }

    public List<ChatMessage> getMessagesBySessionId(String sessionId) {
        List<ChatMessage> messages = this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getTurnIndex));
        log.debug("查询会话消息: sessionId={}, 数量={}", sessionId, messages.size());
        return messages;
    }

    public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        List<ChatMessage> messages = this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getTurnIndex)
                .last("LIMIT " + limit));
        log.debug("查询最近消息: sessionId={}, limit={}, 数量={}", sessionId, limit, messages.size());
        return messages;
    }

    public int getMaxTurnIndex(String sessionId) {
        List<ChatMessage> messages = this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getTurnIndex)
                .last("LIMIT 1"));
        return messages.isEmpty() ? 0 : messages.get(0).getTurnIndex();
    }

    public ConversationCheckpoint getLatestCheckpointBySessionId(String sessionId) {
        return checkpointService.getLatestCheckpoint(sessionId);
    }
}
