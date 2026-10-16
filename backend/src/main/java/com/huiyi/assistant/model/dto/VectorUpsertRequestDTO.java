package com.huiyi.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Python /api/vector/upsert 请求体。
 *
 * @author huiyi
 */
@Data
@AllArgsConstructor
public class VectorUpsertRequestDTO {

    /** collection 名称 */
    @JsonProperty("collection_name")
    private String collectionName;

    @JsonProperty("workspace_id")
    private Long workspaceId;

    /** 分块列表 */
    private List<ParseChunkDTO> chunks;

    public VectorUpsertRequestDTO(String collectionName, List<ParseChunkDTO> chunks) {
        this(collectionName, null, chunks);
    }
}
