package com.huiyi.assistant.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 用户意图枚举。
 *
 * @author huiyi
 */
@Getter
public enum IntentTypeEnum {

    /** 知识库问答 */
    KNOWLEDGE_QA("knowledge_qa"),

    /** 数据查询（Text-to-SQL，P5 接入） */
    DATA_QUERY("data_query");

    /** 接口返回编码 */
    @JsonValue
    private final String code;

    IntentTypeEnum(String code) {
        this.code = code;
    }
}
