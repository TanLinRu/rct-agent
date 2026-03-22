package com.tlq.rectagent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_messages")
public class ChatMessage {

    @TableId(value = "message_id", type = IdType.ASSIGN_ID)
    private String messageId;

    private String sessionId;

    private Integer turnIndex;

    private String role;

    private String contentRaw;

    private String contentProcessed;

    private String sysPromptVer;

    private String tokenUsage;

    private String metadata;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum Role {
        USER, ASSISTANT, SYSTEM, TOOL
    }
}
