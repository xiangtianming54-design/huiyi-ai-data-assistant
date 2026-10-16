package com.huiyi.assistant.service;

import com.huiyi.assistant.common.util.PromptTemplateLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QuestionDecomposeService 单元测试。
 *
 * @author huiyi
 */
class QuestionDecomposeServiceTest {

    private final QuestionDecomposeService service = new QuestionDecomposeService(null, null);

    @Test
    void parsesSingleQuestion() {
        List<String> result = service.parseQuestions("{\"questions\": [\"什么是有效订单？\"]}");
        assertEquals(List.of("什么是有效订单？"), result);
    }

    @Test
    void parsesTwoQuestions() {
        List<String> result = service.parseQuestions(
                "```json\n{\"questions\": [\"什么是有效订单？\", \"上个月销售额前三的商品？\"]}\n```");
        assertEquals(2, result.size());
        assertEquals("上个月销售额前三的商品？", result.get(1));
    }

    @Test
    void returnsEmptyForNonsense() {
        assertTrue(service.parseQuestions("无法分解").isEmpty());
        assertTrue(service.parseQuestions("").isEmpty());
        assertTrue(service.parseQuestions(null).isEmpty());
        assertTrue(service.parseQuestions("{\"other\": []}").isEmpty());
    }

    @Test
    void decomposeFallsBackToOriginalWhenLlmFails() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chat(anyString())).thenThrow(new RuntimeException("llm down"));
        QuestionDecomposeService decomposeService =
                new QuestionDecomposeService(llmService, new PromptTemplateLoader());

        List<String> result = decomposeService.decompose("什么是有效订单？销售额前三？");

        assertEquals(List.of("什么是有效订单？销售额前三？"), result);
    }

    @Test
    void decomposeLimitsToTwoSubQuestions() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chat(anyString()))
                .thenReturn("{\"questions\": [\"q1\", \"q2\", \"q3\"]}");
        QuestionDecomposeService decomposeService =
                new QuestionDecomposeService(llmService, new PromptTemplateLoader());

        List<String> result = decomposeService.decompose("q1 q2 q3");

        assertEquals(2, result.size());
    }

    @Test
    void decomposeMergesParaphrasePairBackToOriginal() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chat(anyString()))
                .thenReturn("{\"questions\": [\"什么是有效订单？\", \"它的定义是什么？\"]}");
        QuestionDecomposeService decomposeService =
                new QuestionDecomposeService(llmService, new PromptTemplateLoader());

        List<String> result = decomposeService.decompose("什么是有效订单？它的定义是什么？");

        assertEquals(List.of("什么是有效订单？它的定义是什么？"), result);
    }

    @Test
    void decomposeKeepsTrueCompoundPair() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chat(anyString()))
                .thenReturn("{\"questions\": [\"什么是有效订单？\", \"上个月销售额前三的商品？\"]}");
        QuestionDecomposeService decomposeService =
                new QuestionDecomposeService(llmService, new PromptTemplateLoader());

        List<String> result = decomposeService.decompose("什么是有效订单？上个月销售额前三的商品？");

        assertEquals(2, result.size());
        assertEquals("上个月销售额前三的商品？", result.get(1));
    }
}
