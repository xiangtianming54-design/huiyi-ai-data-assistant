package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息实体，对应 chat_messages 表。
 *
 * @author huiyi
 */
@Data
@TableName("chat_messages")
public class ChatMessage {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 意图：knowledge_qa / data_query */
    private String intent;

    /** 生成的 SQL（P5 使用） */
    private String sqlText;

    /** 附加元数据 JSON（如引用来源） */
    private String metadataJson;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
