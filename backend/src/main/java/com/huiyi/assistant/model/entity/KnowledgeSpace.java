package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_spaces")
public class KnowledgeSpace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private String name;

    private String description;

    private String status;

    private Boolean isDefault;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
