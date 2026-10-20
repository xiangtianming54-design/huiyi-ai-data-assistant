package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("async_tasks")
public class AsyncTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskType;

    private Long workspaceId;

    private Long userId;

    private String resourceType;

    private String resourceId;

    private String status;

    private Integer attempts;

    private Integer maxAttempts;

    private String payload;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
