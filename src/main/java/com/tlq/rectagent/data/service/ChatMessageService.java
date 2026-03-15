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
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setTurnIndex(turnIndex);
        message.setRole(role);
        message.setContentRaw(contentRaw);
        message.setContentProcessed(contentProcessed);
        this.save(message);
        log.debug("Saved message: {} for session: {}", message.getMessageId(), sessionId);
        return message;
    }

    public List<ChatMessage> getMessagesBySessionId(String sessionId) {
        return this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getTurnIndex));
    }

    public List<ChatMessage> getRecentMessages(String sessionId, int limit) {
        return this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getTurnIndex)
                .last("LIMIT " + limit));
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
