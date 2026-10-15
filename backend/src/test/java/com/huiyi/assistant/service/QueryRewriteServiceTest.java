package com.huiyi.assistant.service;

import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.mapper.ChatMessageMapper;
import com.huiyi.assistant.model.entity.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QueryRewriteService 单元测试。
 *
 * @author huiyi
 */
class QueryRewriteServiceTest {

    private ChatMessageMapper chatMessageMapper;

    private LlmService llmService;

    private QueryRewriteService rewriteService;

    @BeforeEach
    void setUp() {
        chatMessageMapper = mock(ChatMessageMapper.class);
        llmService = mock(LlmService.class);
        rewriteService = new QueryRewriteService(
                new ChatSessionService(chatMessageMapper), llmService, new PromptTemplateLoader());
    }

    @Test
    void passthroughWhenNoHistory() {
        when(chatMessageMapper.selectList(any())).thenReturn(List.of());

        String result = rewriteService.rewrite("s-1", "那第二名呢？");

        assertEquals("那第二名呢？", result);
        // 无历史不调用 LLM（首问零开销）
        verify(llmService, never()).chat(anyString());
    }

    @Test
    void rewritesWithHistory() {
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(
                message("user", "上个月销售额最高的 10 个商品是什么？"),
                message("assistant", "上个月销售额最高的商品是 4K 便携显示器。")));
        when(llmService.chat(anyString())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("上个月销售额最高的 10 个商品"));
            org.junit.jupiter.api.Assertions.assertTrue(prompt.contains("那第二名呢？"));
            return "上个月销售额第二高的商品是什么？";
        });

        String result = rewriteService.rewrite("s-1", "那第二名呢？");

        assertEquals("上个月销售额第二高的商品是什么？", result);
    }

    @Test
    void fallsBackToOriginalWhenLlmFails() {
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(
                message("user", "什么是有效订单？"),
                message("assistant", "有效订单是指支付成功且未退款的订单。")));
        when(llmService.chat(anyString())).thenThrow(new RuntimeException("llm down"));

        String result = rewriteService.rewrite("s-1", "那它的口径呢？");

        assertEquals("那它的口径呢？", result);
    }

    @Test
    void fallsBackWhenLlmReturnsBlank() {
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(
                message("user", "什么是有效订单？")));
        when(llmService.chat(anyString())).thenReturn("  ");

        String result = rewriteService.rewrite("s-1", "那它的口径呢？");

        assertEquals("那它的口径呢？", result);
    }

    private ChatMessage message(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
