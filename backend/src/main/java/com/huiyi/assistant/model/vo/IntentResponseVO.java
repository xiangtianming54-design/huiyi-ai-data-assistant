package com.huiyi.assistant.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 意图预检响应。
 *
 * @author huiyi
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntentResponseVO {

    /** 意图编码：knowledge_qa / data_query / compound（复合问题） */
    private String intent;

    /** 复合问题的子问题列表（仅 intent=compound 时返回） */
    private List<String> questions;
}
