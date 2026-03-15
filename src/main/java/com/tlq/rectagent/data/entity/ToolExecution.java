package com.tlq.rectagent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tool_executions")
public class ToolExecution {

    @TableId(value = "execution_id", type = IdType.ASSIGN_ID)
    private String executionId;

    private String messageId;

    private String mcpServerName;

    private String toolName;

    private String requestPayload;

    private String responsePayload;

    private String status;

    private String errorMessage;

    private Long durationMs;

    private String nacosInstance;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum Status {
        PENDING, SUCCESS, FAILED, TIMEOUT
    }
}
