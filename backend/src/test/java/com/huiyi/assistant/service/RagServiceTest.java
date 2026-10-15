package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.model.dto.VectorSearchResponseDTO;
import com.huiyi.assistant.model.vo.ReferenceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RagService 测试：Mockito 打桩检索与 LLM。
 *
 * @author huiyi
 */
class RagServiceTest {

    private PythonServiceClient pythonServiceClient;

    private RagService ragService;

    private LlmService llmService;

    @BeforeEach
    void setUp() {
        pythonServiceClient = mock(PythonServiceClient.class);
        ragService = new RagService(pythonServiceClient, new PromptTemplateLoader());
        llmService = mock(LlmService.class);
    }

    @Test
    void hitsAboveThresholdBuildPromptAndReferences() {
        when(pythonServiceClient.searchVectors(anyString(), anyInt()))
                .thenReturn(searchResponse(List.of(
                        hit("c1", "有效订单是指支付成功且未退款的订单。", "business-glossary.md", 0.74),
                        hit("c2", "销售额默认只统计 PAID 订单。", "business-glossary.md", 0.66))));

        RagService.RagContext context = ragService.prepare("什么是有效订单？");

        assertTrue(context.needLlm());
        assertTrue(context.prompt().contains("什么是有效订单？"));
        assertTrue(context.prompt().contains("有效订单是指支付成功且未退款的订单。"));
        assertTrue(context.prompt().contains("business-glossary.md"));
        // 同名文件去重，保留最高分
        assertEquals(1, context.references().size());
        assertEquals(0.74, context.references().get(0).getScore(), 0.001);
    }

    @Test
    void emptyHitsReturnNoHitFallback() {
        when(pythonServiceClient.searchVectors(anyString(), anyInt()))
                .thenReturn(searchResponse(List.of()));

        RagService.RagContext context = ragService.prepare("公司年假有多少天？");

        assertFalse(context.needLlm());
        assertEquals(RagService.NO_HIT_FALLBACK, context.fallbackAnswer());
        assertTrue(context.references().isEmpty());
    }

    @Test
    void lowScoreHitsAreFiltered() {
        when(pythonServiceClient.searchVectors(anyString(), anyInt()))
                .thenReturn(searchResponse(List.of(
                        hit("c1", "不相关内容", "other.md", 0.33))));

        RagService.RagContext context = ragService.prepare("公司年假有多少天？");

        assertFalse(context.needLlm());
        assertEquals(RagService.NO_HIT_FALLBACK, context.fallbackAnswer());
    }

    @Test
    void pythonUnavailableReturnsDegradedFallback() {
        when(pythonServiceClient.searchVectors(anyString(), anyInt()))
                .thenThrow(new BizException(ResultCode.PYTHON_SERVICE_UNAVAILABLE));

        RagService.RagContext context = ragService.prepare("什么是有效订单？");

        assertFalse(context.needLlm());
        assertEquals(RagService.SEARCH_DEGRADED_FALLBACK, context.fallbackAnswer());
    }

    @Test
    void answerReturnsLlmContentWithReferences() {
        when(pythonServiceClient.searchVectors(anyString(), anyInt()))
                .thenReturn(searchResponse(List.of(
                        hit("c1", "有效订单定义", "business-glossary.md", 0.74))));
        when(llmService.chat(anyString())).thenReturn("有效订单是指支付成功且未退款的订单。");

        RagService.RagAnswer answer = ragService.answer("什么是有效订单？", llmService);

        assertEquals("有效订单是指支付成功且未退款的订单。", answer.answer());
        assertEquals(1, answer.references().size());
    }

    private VectorSearchResponseDTO.Hit hit(String id, String text, String filename, double score) {
        VectorSearchResponseDTO.Hit hit = new VectorSearchResponseDTO.Hit();
        hit.setId(id);
        hit.setText(text);
        hit.setMetadata(Map.of("filename", filename));
        hit.setScore(score);
        return hit;
    }

    private VectorSearchResponseDTO searchResponse(List<VectorSearchResponseDTO.Hit> hits) {
        VectorSearchResponseDTO response = new VectorSearchResponseDTO();
        response.setSuccess(true);
        response.setHits(hits);
        return response;
    }
}
