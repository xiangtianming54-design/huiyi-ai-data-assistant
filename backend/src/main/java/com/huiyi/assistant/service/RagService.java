package com.huiyi.assistant.service;

import com.huiyi.assistant.common.exception.BizException;
import com.huiyi.assistant.common.result.ResultCode;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.model.dto.VectorSearchResponseDTO;
import com.huiyi.assistant.model.vo.ReferenceVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 问答服务：向量检索 → 上下文拼装 → LLM 生成 → 引用来源。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class RagService {

    /** 检索条数 */
    private static final int TOP_K = 5;

    /** RAG Prompt 模板位置 */
    private static final String RAG_PROMPT_LOCATION = "prompts/rag-system.md";

    /** 知识库未命中兜底话术（场景 5） */
    public static final String NO_HIT_FALLBACK = "当前知识库未找到足够信息，请上传相关制度文档后再试。";

    /** Python 检索服务降级话术 */
    public static final String SEARCH_DEGRADED_FALLBACK = "知识库检索服务暂不可用，请稍后重试。";

    private final PythonServiceClient pythonServiceClient;

    private final PromptTemplateLoader promptTemplateLoader;

    public RagService(PythonServiceClient pythonServiceClient, PromptTemplateLoader promptTemplateLoader) {
        this.pythonServiceClient = pythonServiceClient;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    /**
     * RAG 准备结果：可直接给出兜底答案，或提供 Prompt 交给 LLM 生成。
     *
     * @param fallbackAnswer 兜底答案（非空表示无需调用 LLM）
     * @param prompt         拼装好的 Prompt（fallbackAnswer 为空时有效）
     * @param references     引用来源
     */
    public record RagContext(String fallbackAnswer, String prompt, List<ReferenceVO> references) {

        public boolean needLlm() {
            return fallbackAnswer == null;
        }
    }

    /**
     * 检索并拼装 RAG 上下文：无命中或服务降级时返回兜底，否则给出 Prompt。
     *
     * @param question 用户问题
     * @return RAG 上下文
     */
    public RagContext prepare(String question) {
        List<VectorSearchResponseDTO.Hit> hits;
        try {
            VectorSearchResponseDTO response = pythonServiceClient.searchVectors(question, TOP_K);
            hits = response == null ? null : response.getHits();
        } catch (BizException e) {
            // Python 检索服务不可用 → 降级兜底（4.8）
            log.warn("向量检索降级: code={}, message={}", e.getCode(), e.getMessage());
            return new RagContext(SEARCH_DEGRADED_FALLBACK, null, List.of());
        }

        if (hits == null || hits.isEmpty()) {
            return new RagContext(NO_HIT_FALLBACK, null, List.of());
        }

        String context = buildContext(hits);
        String prompt = promptTemplateLoader.render(RAG_PROMPT_LOCATION,
                Map.of("context", context, "question", question));
        return new RagContext(null, prompt, toReferences(hits));
    }

    /**
     * 非流式 RAG 结果。
     *
     * @param answer     最终回答（LLM 生成或兜底话术）
     * @param references 引用来源
     */
    public record RagAnswer(String answer, List<ReferenceVO> references) {
    }

    /**
     * 非流式 RAG 问答。
     *
     * @param question 用户问题
     * @param llmService LLM 服务
     * @return 回答与引用
     */
    public RagAnswer answer(String question, LlmService llmService) {
        RagContext context = prepare(question);
        if (!context.needLlm()) {
            return new RagAnswer(context.fallbackAnswer(), context.references());
        }
        return new RagAnswer(llmService.chat(context.prompt()), context.references());
    }

    /**
     * 流式 RAG 问答：返回 token 流。
     */
    public Flux<String> stream(RagContext context, LlmService llmService) {
        if (!context.needLlm()) {
            return Flux.just(context.fallbackAnswer());
        }
        return llmService.chatStream(context.prompt());
    }

    /**
     * 拼装上下文：标注来源文件名，便于 LLM 引用。
     */
    private String buildContext(List<VectorSearchResponseDTO.Hit> hits) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            VectorSearchResponseDTO.Hit hit = hits.get(i);
            context.append("【资料").append(i + 1).append("】（来源：")
                    .append(String.valueOf(hit.getMetadata().getOrDefault("filename", "未知")))
                    .append("）\n")
                    .append(hit.getText())
                    .append("\n\n");
        }
        return context.toString().trim();
    }

    /**
     * 提取引用来源：按文件名去重，保留最高分数。
     */
    private List<ReferenceVO> toReferences(List<VectorSearchResponseDTO.Hit> hits) {
        Map<String, Double> scoreBySource = new LinkedHashMap<>();
        for (VectorSearchResponseDTO.Hit hit : hits) {
            String source = String.valueOf(hit.getMetadata().getOrDefault("filename", "未知"));
            scoreBySource.merge(source, hit.getScore(), Math::max);
        }
        return scoreBySource.entrySet().stream()
                .map(e -> new ReferenceVO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }
}
