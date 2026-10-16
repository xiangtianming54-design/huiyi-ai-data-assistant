package com.huiyi.assistant.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QuestionDeduper 单元测试：语义复述合并、真复合保留、embedding 阈值与降级。
 *
 * @author huiyi
 */
class QuestionDeduperTest {

    private final QuestionDeduper deduper = new QuestionDeduper();

    @Test
    void mergesPronounRestatementToOriginal() {
        String original = "什么是有效订单？它的定义是什么？";
        List<String> result = deduper.dedupe(original,
                List.of("什么是有效订单？", "它的定义是什么？"));

        assertEquals(List.of(original), result);
    }

    @Test
    void mergesSameTopicDefinitionParaphrase() {
        String original = "有效订单是什么意思？有效订单的定义是什么？";
        List<String> result = deduper.dedupe(original,
                List.of("有效订单是什么意思？", "有效订单的定义是什么？"));

        assertEquals(List.of(original), result);
    }

    @Test
    void keepsTrueCompoundQuestions() {
        List<String> questions = List.of("什么是有效订单？", "上个月销售额前三的商品？");
        List<String> result = deduper.dedupe("什么是有效订单？上个月销售额前三的商品？", questions);

        assertEquals(questions, result);
    }

    @Test
    void keepsRelatedButDifferentQuestions() {
        List<String> questions = List.of("什么是有效订单？", "取消订单算有效订单吗？");
        List<String> result = deduper.dedupe("什么是有效订单？取消订单算有效订单吗？", questions);

        assertEquals(questions, result);
    }

    @Test
    void singleQuestionPassesThrough() {
        List<String> result = deduper.dedupe("什么是有效订单？", List.of("什么是有效订单？"));
        assertEquals(List.of("什么是有效订单？"), result);
    }

    @Test
    void mergesWhenEmbeddingSaysNearDuplicate() {
        QuestionDeduper withEmbedding = new QuestionDeduper((left, right) -> OptionalDouble.of(0.92));
        String original = "有效订单是啥意思？怎么理解有效订单这个概念？";

        List<String> result = withEmbedding.dedupe(original,
                List.of("有效订单是啥意思？", "怎么理解有效订单这个概念？"));

        assertEquals(List.of(original), result);
    }

    @Test
    void keepsSplitWhenEmbeddingBelowThreshold() {
        QuestionDeduper withEmbedding = new QuestionDeduper((left, right) -> OptionalDouble.of(0.70));
        List<String> questions = List.of("什么是GMV？", "GMV怎么计算？");

        List<String> result = withEmbedding.dedupe("什么是GMV？GMV怎么计算？", questions);

        assertEquals(questions, result);
    }

    @Test
    void embeddingFailureFallsBackToLexicalOnly() {
        QuestionDeduper broken = new QuestionDeduper((left, right) -> {
            throw new RuntimeException("embedding down");
        });
        List<String> questions = List.of("什么是有效订单？", "上个月销售额前三的商品？");

        List<String> result = broken.dedupe("什么是有效订单？上个月销售额前三的商品？", questions);

        assertEquals(questions, result);
    }
}
