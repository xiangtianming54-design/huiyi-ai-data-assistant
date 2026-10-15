package com.huiyi.assistant.model.dto;

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
public class VectorSearchRequestDTO {

    /** 查询文本 */
    private String query;

    /** 返回条数 */
    @JsonProperty("top_k")
    private Integer topK;

    /** collection 名称 */
    @JsonProperty("collection_name")
    private String collectionName;
}
