package com.huiyi.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Python /api/documents/parse 接口响应。
 *
 * @author huiyi
 */
@Data
public class ParseResultDTO {

    /** 文件名 */
    private String filename;

    /** 分块数量 */
    @JsonProperty("chunk_count")
    private Integer chunkCount;

    /** 分块列表 */
    private List<ParseChunkDTO> chunks;
}
