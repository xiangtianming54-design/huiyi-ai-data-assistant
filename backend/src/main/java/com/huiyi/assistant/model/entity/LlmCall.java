package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("llm_calls")
public class LlmCall {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private String runId;
    private String sessionId;
    private Long workspaceId;
    private Long userId;
    private String purpose;
    private String provider;
    private String model;
    private String promptTemplateCode;
    private String promptTemplateVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private BigDecimal costAmount;
    private Integer latencyMs;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
