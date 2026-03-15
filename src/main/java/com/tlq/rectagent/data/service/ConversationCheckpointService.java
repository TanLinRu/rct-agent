package com.tlq.rectagent.data.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tlq.rectagent.data.entity.ConversationCheckpoint;
import com.tlq.rectagent.data.mapper.ConversationCheckpointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCheckpointService extends ServiceImpl<ConversationCheckpointMapper, ConversationCheckpoint> {

    @Transactional
    public ConversationCheckpoint createCheckpoint(String sessionId, String messageId, 
                                                   String stateData, int stepIndex) {
        ConversationCheckpoint checkpoint = new ConversationCheckpoint();
        checkpoint.setSessionId(sessionId);
        checkpoint.setMessageId(messageId);
        checkpoint.setStateData(stateData);
        checkpoint.setStepIndex(stepIndex);
        checkpoint.setIsResumed(false);
        this.save(checkpoint);
        log.info("Created checkpoint: {} for session: {}, step: {}", 
                checkpoint.getCheckpointId(), sessionId, stepIndex);
        return checkpoint;
    }

    public ConversationCheckpoint getLatestCheckpoint(String sessionId) {
        List<ConversationCheckpoint> checkpoints = this.list(
                new LambdaQueryWrapper<ConversationCheckpoint>()
                        .eq(ConversationCheckpoint::getSessionId, sessionId)
                        .orderByDesc(ConversationCheckpoint::getStepIndex)
                        .last("LIMIT 1")
        );
        return checkpoints.isEmpty() ? null : checkpoints.get(0);
    }

    public ConversationCheckpoint getCheckpointByMessageId(String messageId) {
        return this.getOne(new LambdaQueryWrapper<ConversationCheckpoint>()
                .eq(ConversationCheckpoint::getMessageId, messageId)
                .orderByDesc(ConversationCheckpoint::getCreatedAt)
                .last("LIMIT 1"));
    }

    @Transactional
    public void markAsResumed(String checkpointId) {
        this.update(new LambdaUpdateWrapper<ConversationCheckpoint>()
                .eq(ConversationCheckpoint::getCheckpointId, checkpointId)
                .set(ConversationCheckpoint::getIsResumed, true));
    }

    public List<ConversationCheckpoint> getUnresumedCheckpoints(String sessionId) {
        return this.list(new LambdaQueryWrapper<ConversationCheckpoint>()
                .eq(ConversationCheckpoint::getSessionId, sessionId)
                .eq(ConversationCheckpoint::getIsResumed, false)
                .orderByDesc(ConversationCheckpoint::getStepIndex));
    }
}
