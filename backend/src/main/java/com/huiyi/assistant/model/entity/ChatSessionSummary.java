package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话摘要实体，对应 chat_session_summaries。
 *
 * @author huiyi
 */
@Data
@TableName("chat_session_summaries")
public class ChatSessionSummary {

    @TableId(type = IdType.INPUT)
    private String sessionId;

    private String summary;

    private Long coveredUntilId;

    private LocalDateTime updatedAt;
}
