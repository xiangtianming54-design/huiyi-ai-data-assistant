package com.huiyi.assistant.model.dto;

import lombok.Data;

/**
 * Python /api/vector/similarity 响应体。
 *
 * @author huiyi
 */
@Data
public class SimilarityResponseDTO {

    /** 是否成功 */
    private Boolean success;

    /** cosine 相似度，范围 [0, 1] */
    private Double score;
}
