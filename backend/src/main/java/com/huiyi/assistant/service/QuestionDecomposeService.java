package com.huiyi.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huiyi.assistant.common.util.PromptTemplateLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 问题分解服务：将复合问题拆分为最多 2 个独立子问题。
 * 单一问题原样返回；分解失败/结果非法/子问题语义重复时回退为原问题（不影响主链路）。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class QuestionDecomposeService {

    /** 分解 Prompt 模板位置 */
    private static final String DECOMPOSE_PROMPT = "prompts/question-decompose.md";

    /** 最大子问题数 */
    private static final int MAX_SUB_QUESTIONS = 2;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmService llmService;

    private final PromptTemplateLoader promptTemplateLoader;

    private final QuestionDeduper questionDeduper;

    public QuestionDecomposeService(LlmService llmService, PromptTemplateLoader promptTemplateLoader) {
        this(llmService, promptTemplateLoader, new QuestionDeduper());
    }

    @Autowired
    public QuestionDecomposeService(LlmService llmService, PromptTemplateLoader promptTemplateLoader,
                                    QuestionDeduper questionDeduper) {
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
        this.questionDeduper = questionDeduper;
    }

    /**
     * 分解问题。
     *
     * @param question 用户问题（建议先经查询改写处理）
     * @return 子问题列表（1~2 个，永不为空）
     */
    public List<String> decompose(String question) {
        try {
            String prompt = promptTemplateLoader.render(DECOMPOSE_PROMPT, Map.of("question", question));
            String output = llmService.chat(prompt);
            List<String> questions = parseQuestions(output);
            if (questions.isEmpty()) {
                return List.of(question);
            }
            if (questions.size() > MAX_SUB_QUESTIONS) {
                questions = questions.subList(0, MAX_SUB_QUESTIONS);
            }
            questions = questionDeduper.dedupe(question, questions);
            if (questions.size() > 1) {
                log.info("复合问题分解: 「{}」→ {}", question, questions);
            }
            return questions;
        } catch (Exception e) {
            log.warn("问题分解失败，按单问题处理: {}", e.getMessage());
            return List.of(question);
        }
    }

    /**
     * 从 LLM 输出解析子问题列表（提取 JSON 中的 questions 数组）。
     */
    List<String> parseQuestions(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        String json = output.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json.substring(start, end + 1));
            JsonNode questionsNode = node.path("questions");
            if (!questionsNode.isArray()) {
                return List.of();
            }
            List<String> questions = new ArrayList<>();
            for (JsonNode item : questionsNode) {
                String text = item.asText().trim();
                if (!text.isEmpty()) {
                    questions.add(text);
                }
            }
            return questions;
        } catch (Exception e) {
            return List.of();
        }
    }
}
