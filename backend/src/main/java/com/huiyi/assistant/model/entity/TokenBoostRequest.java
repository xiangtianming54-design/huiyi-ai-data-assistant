package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("token_boost_requests")
public class TokenBoostRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applicantUserId;
    private Long reviewerUserId;
    private String status;
    private String reason;
    private String stepName;
    private Integer stepTokens;
    private LocalDate quotaDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
