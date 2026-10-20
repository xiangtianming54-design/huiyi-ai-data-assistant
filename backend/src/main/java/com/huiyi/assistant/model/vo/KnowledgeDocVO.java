package com.huiyi.assistant.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档视图对象。
 *
 * @author huiyi
 */
@Data
@Builder
public class KnowledgeDocVO {

    /** 文档 ID */
    private Long docId;

    /** 文档名称 */
    private String docName;

    /** 文件类型 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 分块数量 */
    private Integer chunkCount;

    /** 状态 */
    private String status;

    /** 所属知识空间 */
    private Long knowledgeSpaceId;

    /** 失败原因（仅 FAILED 有值） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
