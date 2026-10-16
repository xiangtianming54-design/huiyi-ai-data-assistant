package com.huiyi.assistant.service;

import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.config.IntentProperties;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 意图识别（混合分层）：
 * L1 规则快路径（强/弱关键词 + 知识关键词）→ L2 embedding 示例句投票 → L3 LLM 分类兜底。
 * mode=rule 时仅启用规则层（v1 行为）。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class IntentService {

    /** 判定 Prompt 模板位置 */
    private static final String INTENT_CLASSIFY_PROMPT = "prompts/intent-classify.md";

    /** 判定来源层 */
    public enum Layer {
        /** 规则快路径 */
        RULE,
        /** embedding 示例句投票 */
        EMBEDDING,
        /** LLM 分类兜底 */
        LLM,
        /** 兜底默认（LLM 异常） */
        DEFAULT
    }

    /**
     * 意图判定结果。
     *
     * @param intent 意图类型
     * @param layer 判定来源层
     * @param score embedding 最高相似度（未经过 embedding 层时为 null）
     */
    public record IntentDecision(IntentTypeEnum intent, Layer layer, Double score) {
    }

    private final IntentProperties intentProperties;

    private final IntentEmbeddingClassifier intentEmbeddingClassifier;

    private final LlmService llmService;

    private final PromptTemplateLoader promptTemplateLoader;

    public IntentService(IntentProperties intentProperties, IntentEmbeddingClassifier intentEmbeddingClassifier,
                         LlmService llmService, PromptTemplateLoader promptTemplateLoader) {
        this.intentProperties = intentProperties;
        this.intentEmbeddingClassifier = intentEmbeddingClassifier;
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    /**
     * 识别问题意图（仅返回类型，兼容旧调用）。
     */
    public IntentTypeEnum detect(String question) {
        return detectWithDetail(question).intent();
    }

    /**
     * 识别问题意图（返回判定层与分数，用于可解释性记录）。
     *
     * @param question 用户问题
     * @return 判定结果
     */
    public IntentDecision detectWithDetail(String question) {
        if (question == null || question.isBlank()) {
            return new IntentDecision(IntentTypeEnum.KNOWLEDGE_QA, Layer.RULE, null);
        }

        // L1 规则快路径
        if (isDataQueryByRule(question)) {
            return new IntentDecision(IntentTypeEnum.DATA_QUERY, Layer.RULE, null);
        }
        if ("rule".equalsIgnoreCase(intentProperties.getMode())) {
            return new IntentDecision(IntentTypeEnum.KNOWLEDGE_QA, Layer.RULE, null);
        }
        if (isKnowledgeByRule(question)) {
            return new IntentDecision(IntentTypeEnum.KNOWLEDGE_QA, Layer.RULE, null);
        }

        // L2 embedding 示例句投票
        IntentEmbeddingClassifier.Vote vote = intentEmbeddingClassifier.vote(question);
        if (vote.intent() != null) {
            log.info("意图判定[embedding]: question={}, intent={}, score={}", question, vote.intent(), vote.score());
            return new IntentDecision(vote.intent(), Layer.EMBEDDING, vote.score());
        }

        // L3 LLM 分类兜底
        IntentTypeEnum llmIntent = llmClassify(question);
        if (llmIntent != null) {
            log.info("意图判定[llm]: question={}, intent={}", question, llmIntent);
            return new IntentDecision(llmIntent, Layer.LLM, vote.score());
        }
        log.info("意图判定[default]: question={}, 兜底 knowledge_qa", question);
        return new IntentDecision(IntentTypeEnum.KNOWLEDGE_QA, Layer.DEFAULT, vote.score());
    }

    /**
     * 规则判定数据查询：强关键词命中，或弱关键词与业务名词共现。
     */
    private boolean isDataQueryByRule(String question) {
        if (intentProperties.getStrongKeywords().stream().anyMatch(question::contains)) {
            return true;
        }
        boolean hasWeakKeyword = intentProperties.getWeakKeywords().stream().anyMatch(question::contains);
        boolean hasBusinessNoun = intentProperties.getBusinessNouns().stream().anyMatch(question::contains);
        return hasWeakKeyword && hasBusinessNoun;
    }

    /**
     * 规则判定知识问答：定义/口径类关键词命中。
     */
    private boolean isKnowledgeByRule(String question) {
        return intentProperties.getKnowledgeKeywords().stream().anyMatch(question::contains);
    }

    /**
     * LLM 分类兜底：输出含 data_query 则数据查询，否则知识问答；异常返回 null。
     */
    private IntentTypeEnum llmClassify(String question) {
        try {
            String prompt = promptTemplateLoader.render(INTENT_CLASSIFY_PROMPT, Map.of("question", question));
            String output = llmService.chat(prompt);
            return output != null && output.contains("data_query")
                    ? IntentTypeEnum.DATA_QUERY : IntentTypeEnum.KNOWLEDGE_QA;
        } catch (Exception e) {
            log.warn("LLM 意图分类失败: {}", e.getMessage());
            return null;
        }
    }
}
