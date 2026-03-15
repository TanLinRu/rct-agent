package com.tlq.rectagent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("profile_changes")
public class ProfileChange {

    @TableId(value = "change_id", type = IdType.ASSIGN_ID)
    private String changeId;

    private String userId;

    private String fieldName;

    private String oldValue;

    private String newValue;

    private String reasoning;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
