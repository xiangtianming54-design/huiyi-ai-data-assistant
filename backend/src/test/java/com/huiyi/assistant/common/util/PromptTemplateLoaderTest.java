package com.huiyi.assistant.common.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PromptTemplateLoader 占位替换单元测试。
 *
 * @author huiyi
 */
class PromptTemplateLoaderTest {

    private final PromptTemplateLoader loader = new PromptTemplateLoader();

    @Test
    void rendersContextAndQuestion() {
        String result = loader.render("prompts/rag-system.md",
                Map.of("context", "有效订单是指支付成功且未退款的订单。", "question", "什么是有效订单？"));

        assertTrue(result.contains("有效订单是指支付成功且未退款的订单。"));
        assertTrue(result.contains("什么是有效订单？"));
        assertFalse(result.contains("{{context}}"));
        assertFalse(result.contains("{{question}}"));
    }

    @Test
    void missingVariableRendersAsEmpty() {
        String result = loader.render("prompts/rag-system.md", Map.of("context", "资料"));

        assertFalse(result.contains("{{question}}"));
        assertTrue(result.contains("资料"));
    }

    @Test
    void specialRegexCharactersAreEscaped() {
        String result = loader.render("prompts/rag-system.md",
                Map.of("context", "价格是 $100 (含税)", "question", "合计 ${amount}?"));

        assertTrue(result.contains("价格是 $100 (含税)"));
        assertTrue(result.contains("合计 ${amount}?"));
    }
}
