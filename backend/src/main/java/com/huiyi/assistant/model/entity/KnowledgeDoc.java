package com.huiyi.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档元数据实体，对应 knowledge_docs 表。
 *
 * @author huiyi
 */
@Data
@TableName("knowledge_docs")
public class KnowledgeDoc {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档名称 */
    private String docName;

    /** 文件类型（扩展名） */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 分块数量 */
    private Integer chunkCount;

    /** 状态：UPLOADED/PARSING/READY/FAILED/ARCHIVED */
    private String status;

    private Long workspaceId;

    private Long knowledgeSpaceId;

    private Long createdBy;

    private String storageKey;

    private String errorMessage;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
