package com.huiyi.assistant.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 意图预检响应。
 *
 * @author huiyi
 */
@Data
@AllArgsConstructor
public class IntentResponseVO {

    /** 意图编码：knowledge_qa / data_query */
    private String intent;
}
