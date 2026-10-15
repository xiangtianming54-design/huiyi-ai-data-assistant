package com.huiyi.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Python /api/data/profile 响应体。
 *
 * @author huiyi
 */
@Data
public class DataProfileResponseDTO {

    /** 行数 */
    @JsonProperty("row_count")
    private Integer rowCount;

    /** 列名 */
    private List<String> columns;

    /** 数值列摘要 */
    private Map<String, Object> summary;

    /** 推荐图表类型 */
    @JsonProperty("chart_type")
    private String chartType;

    /** 空结果标记 */
    private Boolean empty;
}
