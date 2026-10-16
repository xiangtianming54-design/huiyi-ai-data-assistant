package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_events")
public class AuditEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long workspaceId;
    private Long userId;
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    private String resultStatus;
    private String ip;
    private String userAgent;
    private String detailJson;
    private LocalDateTime createdAt;
}
