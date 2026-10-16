package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("refresh_tokens")
public class RefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tokenHash;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String replacedByTokenHash;
    private String userAgent;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
