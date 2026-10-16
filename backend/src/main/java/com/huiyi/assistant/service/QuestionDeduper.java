package com.huiyi.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * 子问题去重：词面主题归并 + 可选 embedding 相似度。
 * 语义复述/指代回退为原问题；真复合问题保持拆分。embedding 失败不影响词面判定。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class QuestionDeduper {

    /** embedding cosine 超过该值视为同义复述 */
    static final double EMBEDDING_THRESHOLD = 0.85;

    private static final Pattern PUNCT = Pattern.compile("[？?；;。.!！,，、\\s]+");

    private final TextSimilarity textSimilarity;

    public QuestionDeduper() {
        this((TextSimilarity) null);
    }

    public QuestionDeduper(TextSimilarity textSimilarity) {
        this.textSimilarity = textSimilarity;
    }

    @Autowired
    public QuestionDeduper(PythonServiceClient pythonServiceClient) {
        this((left, right) -> {
            try {
                return OptionalDouble.of(pythonServiceClient.similarity(left, right));
            } catch (Exception e) {
                log.warn("embedding 相似度调用失败: {}", e.getMessage());
                return OptionalDouble.empty();
            }
        });
    }

    /**
     * 对分解结果去重。相似则回退原问题。
     *
     * @param original 原始问题
     * @param questions 分解后的子问题
     * @return 1 个（合并）或原列表（保留拆分）
     */
    public List<String> dedupe(String original, List<String> questions) {
        if (questions == null || questions.isEmpty()) {
            return original == null || original.isBlank() ? List.of() : List.of(original);
        }
        if (questions.size() == 1 || original == null || original.isBlank()) {
            return questions;
        }
        String first = questions.get(0);
        String second = questions.get(1);
        if (shouldMerge(first, second)) {
            log.info("子问题语义重复，回退原问题: 「{}」/「{}」", first, second);
            return List.of(original);
        }
        return questions;
    }

    boolean shouldMerge(String left, String right) {
        if (isLexicalDuplicate(left, right)) {
            return true;
        }
        return isEmbeddingDuplicate(left, right);
    }

    private boolean isLexicalDuplicate(String left, String right) {
        String topicLeft = topic(left);
        String topicRight = topic(right);
        if (topicLeft.isEmpty() || topicRight.isEmpty()) {
            return true;
        }
        return topicLeft.equals(topicRight);
    }

    private boolean isEmbeddingDuplicate(String left, String right) {
        if (textSimilarity == null) {
            return false;
        }
        try {
            OptionalDouble score = textSimilarity.score(left, right);
            return score.isPresent() && score.getAsDouble() >= EMBEDDING_THRESHOLD;
        } catch (Exception e) {
            log.warn("embedding 相似度失败，仅按词面判定: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 抽出问题主题：去掉问句套话与指代后的核心词。
     */
    String topic(String question) {
        if (question == null) {
            return "";
        }
        String text = PUNCT.matcher(question).replaceAll("");
        text = text.replaceAll("^(请问|请|帮我看看|帮我)", "");
        text = text.replaceAll("^(什么是|啥是|怎么理解|如何理解)", "");
        text = text.replaceAll("(是什么意思|是啥意思|的定义是什么|是什么|的定义|指的是什么|什么意思)$", "");
        text = text.replaceAll("^(它的|他的|她的|这个|那个|这种|那种|其|该|它|这|那)", "");
        return text;
    }
}
