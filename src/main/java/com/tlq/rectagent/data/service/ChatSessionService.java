package com.tlq.rectagent.data.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tlq.rectagent.data.entity.ChatSession;
import com.tlq.rectagent.data.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    public ChatSession createSession(String userId, String traceId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTraceId(traceId);
        session.setStartTime(LocalDateTime.now());
        session.setStatus(ChatSession.Status.NORMAL.name());
        session.setTotalTokens(0);
        this.save(session);
        log.info("Created new session: {} for user: {}", session.getSessionId(), userId);
        return session;
    }

    public ChatSession getSessionById(String sessionId) {
        return this.getById(sessionId);
    }

    public List<ChatSession> getSessionsByUserId(String userId, int limit) {
        return this.list(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getStartTime)
                .last("LIMIT " + limit));
    }

    @Transactional
    public void updateSessionSummary(String sessionId, String summary) {
        this.update(new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .set(ChatSession::getSummarySnapshot, summary));
    }

    @Transactional
    public void updateSessionTokens(String sessionId, int tokens) {
        ChatSession session = this.getById(sessionId);
        if (session != null) {
            session.setTotalTokens(session.getTotalTokens() + tokens);
            this.updateById(session);
        }
    }

    @Transactional
    public void endSession(String sessionId, String status) {
        this.update(new LambdaUpdateWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .set(ChatSession::getEndTime, LocalDateTime.now())
                .set(ChatSession::getStatus, status));
    }
}
