package com.huiyi.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Python /api/vector/search 请求体。
 *
 * @author huiyi
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VectorSearchRequestDTO {

    /** 查询文本 */
    private String query;

    /** 返回条数 */
    @JsonProperty("top_k")
    private Integer topK;

    /** collection 名称 */
    @JsonProperty("collection_name")
    private String collectionName;

    @JsonProperty("workspace_id")
    private Long workspaceId;

    public VectorSearchRequestDTO(String query, Integer topK, String collectionName) {
        this(query, topK, collectionName, null);
    }
}
