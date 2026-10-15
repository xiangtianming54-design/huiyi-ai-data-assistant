package com.huiyi.assistant.service;

import com.huiyi.assistant.model.enums.IntentTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IntentService 规则版意图识别单元测试。
 *
 * @author huiyi
 */
class IntentServiceTest {

    private final IntentService intentService = new IntentService();

    @ParameterizedTest
    @ValueSource(strings = {
            "上个月销售额最高的 10 个商品是什么？",
            "各渠道订单量对比",
            "华东地区销售趋势如何？",
            "销量排名前五的商品",
            "本月有多少笔订单？"
    })
    void dataQueryQuestions(String question) {
        assertEquals(IntentTypeEnum.DATA_QUERY, intentService.detect(question));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "什么是有效订单？",
            "公司年假有多少天？",
            "华东地区包含哪些省份？"
    })
    void knowledgeQuestions(String question) {
        assertEquals(IntentTypeEnum.KNOWLEDGE_QA, intentService.detect(question));
    }

    @Test
    void blankQuestionDefaultsToKnowledge() {
        assertEquals(IntentTypeEnum.KNOWLEDGE_QA, intentService.detect(""));
        assertEquals(IntentTypeEnum.KNOWLEDGE_QA, intentService.detect(null));
    }
}
