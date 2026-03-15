package com.tlq.rectagent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_sessions")
public class ChatSession {

    @TableId(value = "session_id", type = IdType.ASSIGN_ID)
    private String sessionId;

    private String userId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer totalTokens;

    private String status;

    private String summarySnapshot;

    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum Status {
        NORMAL, ABORTED, ERROR
    }
}
