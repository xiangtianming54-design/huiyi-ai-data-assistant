package com.huiyi.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorBackfillRequestDTO {

    @JsonProperty("collection_name")
    private String collectionName;

    private List<Assignment> assignments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assignment {

        @JsonProperty("workspace_id")
        private Long workspaceId;

        @JsonProperty("doc_id")
        private Long docId;

        @JsonProperty("knowledge_space_id")
        private Long knowledgeSpaceId;
    }
}
