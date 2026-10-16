package com.huiyi.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Python 解析接口返回的单个分块。
 *
 * @author huiyi
 */
@Data
public class ParseChunkDTO {

    /** 分块 ID */
    private String id;

    /** 分块正文 */
    private String text;

    /** 分块元数据 */
    private Metadata metadata;

    /**
     * 分块元数据，字段与 Python 端 snake_case 契约对应。
     */
    @Data
    public static class Metadata {

        /** 来源文件名 */
        private String filename;

        /** 分块序号 */
        @JsonProperty("chunk_index")
        private Integer chunkIndex;

        /** 来源文件类型 */
        @JsonProperty("source_type")
        private String sourceType;

        @JsonProperty("workspace_id")
        private String workspaceId;

        @JsonProperty("doc_id")
        private String docId;
    }
}
