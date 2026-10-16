package com.huiyi.assistant.service;

import com.huiyi.assistant.config.IntentProperties;
import com.huiyi.assistant.model.dto.VectorSearchResponseDTO;
import com.huiyi.assistant.model.enums.IntentTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图 embedding 分类器：将两类意图示例句写入向量库，用户问题与示例句做最近邻投票。
 * 混合分层第二层 —— 规则无法判定时用语义相似度分类。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class IntentEmbeddingClassifier implements ApplicationRunner {

    /** 意图示例句 collection */
    public static final String INTENT_COLLECTION = "intent_examples";

    /** 投票取回条数 */
    private static final int VOTE_TOP_K = 3;

    /**
     * 投票结果。
     *
     * @param intent 胜出意图（无置信结果时为 null）
     * @param score 最高相似度分数
     */
    public record Vote(IntentTypeEnum intent, double score) {
    }

    private final IntentProperties intentProperties;

    private final PythonServiceClient pythonServiceClient;

    public IntentEmbeddingClassifier(IntentProperties intentProperties, PythonServiceClient pythonServiceClient) {
        this.intentProperties = intentProperties;
        this.pythonServiceClient = pythonServiceClient;
    }

    /**
     * 启动时写入示例句（幂等，同 id 覆盖；rule 模式下跳过）。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!"hybrid".equalsIgnoreCase(intentProperties.getMode())) {
            return;
        }
        List<Map<String, Object>> chunks = new ArrayList<>();
        int index = 0;
        for (String text : intentProperties.getExamples().getDataQuery()) {
            chunks.add(chunk("intent-dq-" + index, index, text, IntentTypeEnum.DATA_QUERY));
            index++;
        }
        for (String text : intentProperties.getExamples().getKnowledgeQa()) {
            chunks.add(chunk("intent-kq-" + index, index, text, IntentTypeEnum.KNOWLEDGE_QA));
            index++;
        }
        try {
            pythonServiceClient.upsertChunks(INTENT_COLLECTION, chunks);
            log.info("意图示例句已写入向量库: {} 条", chunks.size());
        } catch (Exception e) {
            log.warn("意图示例句写入失败（本层将不可用，自动降级）: {}", e.getMessage());
        }
    }

    /**
     * 对问题做 embedding 最近邻投票。
     *
     * @param question 用户问题
     * @return 投票结果（低于阈值或异常时 intent 为 null）
     */
    public Vote vote(String question) {
        try {
            VectorSearchResponseDTO response = pythonServiceClient.searchVectors(question, VOTE_TOP_K, INTENT_COLLECTION);
            if (response == null || response.getHits() == null || response.getHits().isEmpty()) {
                return new Vote(null, 0);
            }
            Map<String, Double> scoreByIntent = new HashMap<>();
            double topScore = 0;
            for (VectorSearchResponseDTO.Hit hit : response.getHits()) {
                String intent = String.valueOf(hit.getMetadata().getOrDefault("intent", ""));
                if (intent.isBlank()) {
                    continue;
                }
                scoreByIntent.merge(intent, hit.getScore(), Double::sum);
                topScore = Math.max(topScore, hit.getScore());
            }
            if (scoreByIntent.isEmpty() || topScore < intentProperties.getEmbeddingThreshold()) {
                return new Vote(null, topScore);
            }
            String winner = scoreByIntent.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            IntentTypeEnum intent = "data_query".equals(winner) ? IntentTypeEnum.DATA_QUERY : IntentTypeEnum.KNOWLEDGE_QA;
            return new Vote(intent, topScore);
        } catch (Exception e) {
            log.warn("意图 embedding 投票失败: {}", e.getMessage());
            return new Vote(null, 0);
        }
    }

    /**
     * 构造示例句分块。
     */
    private Map<String, Object> chunk(String id, int index, String text, IntentTypeEnum intent) {
        return Map.of(
                "id", id,
                "text", text,
                "metadata", Map.of(
                        "filename", "intent-examples",
                        "chunk_index", index,
                        "source_type", "intent",
                        "intent", intent.getCode()));
    }
}
