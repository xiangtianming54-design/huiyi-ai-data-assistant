package com.huiyi.assistant.service;

import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.config.IntentProperties;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IntentService 混合分层测试（rule → embedding → LLM）。
 *
 * @author huiyi
 */
class IntentServiceHybridTest {

    private IntentEmbeddingClassifier classifier;

    private LlmService llmService;

    private IntentService intentService;

    @BeforeEach
    void setUp() {
        IntentProperties properties = new IntentProperties();
        properties.setMode("hybrid");
        classifier = mock(IntentEmbeddingClassifier.class);
        llmService = mock(LlmService.class);
        intentService = new IntentService(properties, classifier, llmService, new PromptTemplateLoader());
    }

    @Test
    void strongKeywordShortCircuitsRuleLayer() {
        IntentService.IntentDecision decision = intentService.detectWithDetail("上个月销售额最高的商品？");

        assertEquals(IntentTypeEnum.DATA_QUERY, decision.intent());
        assertEquals(IntentService.Layer.RULE, decision.layer());
        // 快路径命中后不再调用 embedding
        verify(classifier, never()).vote(anyString());
    }

    @Test
    void knowledgeKeywordShortCircuitsRuleLayer() {
        IntentService.IntentDecision decision = intentService.detectWithDetail("什么是有效订单？");

        assertEquals(IntentTypeEnum.KNOWLEDGE_QA, decision.intent());
        assertEquals(IntentService.Layer.RULE, decision.layer());
        verify(classifier, never()).vote(anyString());
    }

    @Test
    void fallsToEmbeddingWhenRuleMisses() {
        when(classifier.vote(anyString()))
                .thenReturn(new IntentEmbeddingClassifier.Vote(IntentTypeEnum.DATA_QUERY, 0.72));

        IntentService.IntentDecision decision = intentService.detectWithDetail("卖得最好的是哪些？");

        assertEquals(IntentTypeEnum.DATA_QUERY, decision.intent());
        assertEquals(IntentService.Layer.EMBEDDING, decision.layer());
        assertEquals(0.72, decision.score(), 0.001);
    }

    @Test
    void fallsToLlmWhenEmbeddingNotConfident() {
        when(classifier.vote(anyString()))
                .thenReturn(new IntentEmbeddingClassifier.Vote(null, 0.4));
        when(llmService.chat(anyString())).thenReturn("data_query");

        IntentService.IntentDecision decision = intentService.detectWithDetail("帮我看看卖得咋样");

        assertEquals(IntentTypeEnum.DATA_QUERY, decision.intent());
        assertEquals(IntentService.Layer.LLM, decision.layer());
    }

    @Test
    void defaultsToKnowledgeWhenLlmFails() {
        when(classifier.vote(anyString()))
                .thenReturn(new IntentEmbeddingClassifier.Vote(null, 0.3));
        when(llmService.chat(anyString())).thenThrow(new RuntimeException("llm down"));

        IntentService.IntentDecision decision = intentService.detectWithDetail("随便聊聊");

        assertEquals(IntentTypeEnum.KNOWLEDGE_QA, decision.intent());
        assertEquals(IntentService.Layer.DEFAULT, decision.layer());
    }
}
