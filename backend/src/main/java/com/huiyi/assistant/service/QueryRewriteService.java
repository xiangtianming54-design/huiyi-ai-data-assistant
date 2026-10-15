package com.huiyi.assistant.service;

import com.huiyi.assistant.common.util.PromptTemplateLoader;
import com.huiyi.assistant.model.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询改写服务：多轮对话中，将依赖上下文的追问改写为独立问题（Conversational RAG 关键步骤）。
 * 无历史时原样透传（零开销）；改写失败回退原问题（不影响主链路）。
 *
 * @author huiyi
 */
@Slf4j
@Service
public class QueryRewriteService {

    /** 改写 Prompt 模板位置 */
    private static final String CONDENSE_PROMPT = "prompts/condense-question.md";

    /** 参与改写的历史窗口（最近 6 条，约 3 轮） */
    private static final int HISTORY_LIMIT = 6;

    private final ChatSessionService chatSessionService;

    private final LlmService llmService;

    private final PromptTemplateLoader promptTemplateLoader;

    public QueryRewriteService(ChatSessionService chatSessionService, LlmService llmService,
                               PromptTemplateLoader promptTemplateLoader) {
        this.chatSessionService = chatSessionService;
        this.llmService = llmService;
        this.promptTemplateLoader = promptTemplateLoader;
    }

    /**
     * 结合会话历史，将问题改写为独立问题。
     *
     * @param sessionId 会话 ID
     * @param question 用户原始问题
     * @return 独立问题（无历史或改写失败时返回原问题）
     */
    public String rewrite(String sessionId, String question) {
        List<ChatMessage> history = chatSessionService.getRecentMessages(sessionId, HISTORY_LIMIT);
        if (history.isEmpty()) {
            return question;
        }
        try {
            String historyText = history.stream()
                    .map(m -> (ChatSessionService.ROLE_USER.equals(m.getRole()) ? "用户: " : "助手: ")
                            + m.getContent())
                    .collect(Collectors.joining("\n"));
            String prompt = promptTemplateLoader.render(CONDENSE_PROMPT,
                    Map.of("history", historyText, "question", question));
            String rewritten = llmService.chat(prompt);
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String condensed = rewritten.trim();
            log.info("问题改写: 「{}」→「{}」", question, condensed);
            return condensed;
        } catch (Exception e) {
            log.warn("问题改写失败，回退原问题: {}", e.getMessage());
            return question;
        }
    }
}
