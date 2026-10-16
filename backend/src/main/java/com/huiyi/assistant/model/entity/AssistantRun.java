package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("assistant_runs")
public class AssistantRun {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String requestId;
    private String sessionId;
    private Long workspaceId;
    private Long userId;
    private String entrypoint;
    private String question;
    private String intent;
    private String status;
    private String errorMessage;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal costAmount;
    private Integer totalLatencyMs;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
