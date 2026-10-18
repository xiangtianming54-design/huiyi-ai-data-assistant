package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assistant_run_steps")
public class AssistantRunStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private Integer stepNo;
    private String stepType;
    private String toolName;
    private String status;
    private String provider;
    private String model;
    private String inputSummary;
    private String outputSummary;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer latencyMs;
    private String errorMessage;
    private LocalDateTime createdAt;
}
