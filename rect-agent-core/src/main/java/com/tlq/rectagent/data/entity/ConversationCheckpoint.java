package com.tlq.rectagent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation_checkpoints")
public class ConversationCheckpoint {

    @TableId(value = "checkpoint_id", type = IdType.ASSIGN_ID)
    private String checkpointId;

    private String sessionId;

    private String messageId;

    private String stateData;

    private Integer stepIndex;

    private Boolean isResumed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
