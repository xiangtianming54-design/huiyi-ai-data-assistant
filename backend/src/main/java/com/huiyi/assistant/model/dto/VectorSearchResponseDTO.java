package com.huiyi.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Python /api/vector/search 响应体。
 *
 * @author huiyi
 */
@Data
public class VectorSearchResponseDTO {

    /** 是否成功 */
    private Boolean success;

    /** 命中列表 */
    private List<Hit> hits;

    /** 查询向量化的服务商 total_tokens；响应没有该字段时为 null */
    @JsonProperty("embedding_tokens")
    private Integer embeddingTokens;

    /**
     * 单个命中项。
     */
    @Data
    public static class Hit {

        /** 分块 ID */
        private String id;

        /** 分块正文 */
        private String text;

        /** 元数据（filename / chunk_index / source_type） */
        private Map<String, Object> metadata;

        /** 相似度分数（cosine，越大越相关） */
        private Double score;
    }
}
